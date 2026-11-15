"""에뮬레이터 WAV 출력(QEMU_AUDIO_DRV=wav)의 증가 시각을 기록하고, 영상 타임라인에 맞춰 음성 트랙을 재구성한다.

QEMU wav 드라이버는 소리가 날 때만 데이터를 쓰기 때문에 파일 위치 ≠ 실제 시간이다.
녹화 중 파일 크기를 짧은 주기로 기록해 두면, 각 소리 조각이 '언제' 재생됐는지 알 수 있다.

  track:  python3 wav_tracker.py track <wav> <log.csv>          (Ctrl+C / SIGTERM 으로 종료)
  build:  python3 wav_tracker.py build <wav> <log.csv> <video_start_epoch> <duration_s> <out.wav>
"""
import signal
import struct
import sys
import time
import wave
import os

HEADER = 44


def track(wav_path, log_path):
    stop = {"v": False}
    signal.signal(signal.SIGTERM, lambda *_: stop.update(v=True))
    with open(log_path, "w") as f:
        try:
            while not stop["v"]:
                try:
                    size = os.path.getsize(wav_path)
                except OSError:
                    size = 0
                f.write(f"{time.time():.3f},{size}\n")
                f.flush()
                time.sleep(0.05)
        except KeyboardInterrupt:
            pass


def build(wav_path, log_path, video_start, duration, out_path):
    raw = open(wav_path, "rb").read()
    ch, rate, bits = struct.unpack("<H", raw[22:24])[0], struct.unpack("<I", raw[24:28])[0], struct.unpack("<H", raw[34:36])[0]
    frame = ch * bits // 8
    bps = rate * frame
    rows = [tuple(map(float, l.split(","))) for l in open(log_path) if l.strip()]

    # 기록 주기(0.05초)마다 새로 쓰인 바이트를 '조각'으로 본다.
    # 각 조각은 '도착 시각 - 조각 길이'에 놓아, 녹음이 건너뛴 구간이 있어도 뒤로 갈수록 밀리지 않게 한다.
    segments = []
    prev_t, prev_s = rows[0]
    for t, s in rows[1:]:
        if s > prev_s:
            dur = (s - prev_s) / bps
            segments.append({"t": t - dur, "start": prev_s, "end": s})
        prev_t, prev_s = t, s

    total = int(duration * rate) * frame
    out = bytearray(total)
    cursor = 0
    for seg in segments:
        a, b = int(max(seg["start"], HEADER)), int(seg["end"])
        a -= (a - HEADER) % frame
        b -= (b - HEADER) % frame
        chunk = raw[a:b]
        pos = int((seg["t"] - video_start) * bps)
        pos -= pos % frame
        pos = max(pos, cursor)  # 겹치면 이어 붙임
        if pos < 0:
            chunk = chunk[-pos:]
            pos = 0
        end = min(pos + len(chunk), total)
        out[pos:end] = chunk[: end - pos]
        cursor = end
    print(f"조각 {len(segments)}개, 마지막 끝 {cursor / bps:6.2f}s")

    with wave.open(out_path, "wb") as w:
        w.setnchannels(ch)
        w.setsampwidth(bits // 8)
        w.setframerate(rate)
        w.writeframes(bytes(out))


if __name__ == "__main__":
    if sys.argv[1] == "track":
        track(sys.argv[2], sys.argv[3])
    else:
        build(sys.argv[2], sys.argv[3], float(sys.argv[4]), float(sys.argv[5]), sys.argv[6])
