# RideDaemon EOF Crash Fix

## Problem Summary

**Crash**: `RideDaemon fatal callback: 2 (error reading header: EOF)` after 4-6 minutes of streaming Android Auto to T-Box.

**Root Cause**: Deadlock between the AVC encoder drain thread and the RideDaemon socket reader thread.

---

## Detailed Analysis

### From the Diagnostic Log (MOTO-HUB-diagnostics-20260720-190731-kurrvva.txt)

**Timeline**:
- **21:59:21** — Android Auto starts, PXC heartbeats every ~2 seconds, frames at 30fps
- **22:02:51-53** — FPS drop anomaly: 25→23→22fps 
- **22:04:27** — Another FPS drop to 25fps
- **22:04:39** — **CRASH**: "RideDaemon fatal callback: 2 (error reading header: EOF)"

**Protocol stats at crash**:
```
pxcRx=306 (last=1283ms ago)        ← Heartbeat still arriving every ~2s
mediaCtrlRx=3 (last=600982ms ago)  ← MediaCtrl DEAD for 10+ MINUTES
framesOffered=11553 (last=17ms)    ← Frames continue being offered
```

### The Deadlock

```
Thread: MotoHubAvcDrain (Encoder)
├─ drainLoop() dequeues output buffer
├─ Calls onAccessUnit(frame) callback
└─ Blocks on handle.transport.offerAccessUnit(accessUnit)
   └─ Calls RideDaemonTransport.offerAccessUnit()
      └─ Calls activeSession.pushFrame() [JNI call to Go]
         └─ BLOCKS waiting for T-Box to accept frame
            (T-Box socket buffer is congested)

Thread: Go RideDaemon (Socket Reader) 
├─ Tries to read next PXC command header from socket
├─ Cannot proceed (blocked on frame write backpressure?)
└─ Eventually times out after ~30 seconds
   └─ Socket closes with EOF
```

### Why FPS Drops Precede the Crash

When `pushFrame()` starts blocking (T-Box congestion):
1. The drain thread stalls inside `onAccessUnit()`
2. MediaCodec output buffers accumulate, not released on time
3. Encoder slows down to avoid buffer overflow → FPS drops (observed: 19-25fps)
4. If the congestion persists > socket read timeout (30s), EOF occurs

### Why mediaCtrlRx Remains Stale

The T-Box has two communication channels:
- **PXC** (heartbeat/control): Managed by Go, unaffected by drain thread stall
- **MediaCtrl** (video commands): Would need the socket reader to be responsive

With the drain thread stalled, any response processing is delayed. The 10-minute staleness indicates the T-Box stopped sending control commands because it detected the client isn't responsive.

---

## Solution: Bounded pushFrame() Queue with Timeout

Implemented in `RideDaemonTransport.kt`. The encoder drain thread no longer calls the native
`pushFrame()` itself.

- **Dedicated push thread.** `pushFrameExecutor` is a single-threaded `ThreadPoolExecutor`
  (`MotoHubPushFrame`) with an `ArrayBlockingQueue(1)`: one native call in flight and at most one
  access unit waiting behind it. The first version used an unbounded single-thread executor; an
  intermediate zero-capacity queue made a short `pushFrame()` overlap look like a dead session, so
  the queue holds exactly one.
- **Submission grace period.** `submitToPushQueue()` retries a rejected submission every
  `PUSH_FRAME_SUBMIT_RETRY_DELAY_MS` (5 ms) for up to `PUSH_FRAME_SUBMIT_WAIT_MS` (1 s). Only a
  queue that stays blocked for the whole period is reported to the caller as a failure. Each
  rejection is counted in `framesRejected`.
- **Push timeout.** `offerAccessUnit()` waits on the submitted call for at most
  `PUSH_FRAME_TIMEOUT_MS` (5 s). On timeout the frame is dropped, `framesTimedOut` is incremented
  and a `TBOX` warning is logged.
- **Stills share the path.** `offerStillFrame()` pushes JPEG stills through the same queue, grace
  period and counters, for dashboards that are sent still images instead of H.264.
- **Diagnostics.** `protocolSnapshot()` reports `frameTimeouts` and `frameRejections` next to
  `pxcRx`, `mediaCtrlRx` and `framesOffered`.

---

## How This Fixes the Crash

1. **The drain thread cannot stall indefinitely.** It waits at most 1 s to submit and 5 s for the
   native call, then drops the frame and returns.
2. **The socket reader stays responsive.** Heartbeats keep flowing, so the 30-second read timeout
   that produced the EOF is not reached through this path.
3. **Congestion is visible.** A climbing `frameTimeouts` or `frameRejections` in the protocol
   stats means the T-Box is not accepting frames.

---

## Expected Behavior

Normal streaming:
```
framesOffered=11553, frameTimeouts=0, frameRejections=0
```

T-Box congested:
```
framesOffered=11600, frameTimeouts=15, frameRejections=40
```

In the log:
```
WARNING  TBOX: AVC frame dropped: pushFrame() exceeded 5000ms timeout.
               The T-Box may be unresponsive. Timeouts: 15
```

The app keeps streaming with some frame loss instead of crashing.

---

## Open Improvements

1. **Watchdog check** on `frameTimeouts` crossing a threshold, to trigger recovery before the
   stream has visibly stalled. The counters are reported but nothing acts on them yet.
2. **Tune `PUSH_FRAME_TIMEOUT_MS`** down if field logs show the T-Box link is consistently faster.

---

## Testing

Manual test:
1. Start Android Auto projection.
2. Congest the T-Box link, for example by reducing Wi-Fi bandwidth.
3. Observe that FPS may drop but no EOF crash occurs.
4. Check the log: `frameTimeouts` or `frameRejections` should increase.
5. When congestion clears, streaming resumes normally.

There is no automated test that reproduces the original deadlock.
