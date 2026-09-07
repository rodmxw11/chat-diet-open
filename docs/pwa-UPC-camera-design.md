# Prompt

i really like the way sams club scan and go app captures upc codes; once you click the button the camera opens up and searches for the upc code, it visualy brackets it and takes the scan. very nice; is this code available in opensource? would you be able to duplicate it on a pixel 8 pwa app

# Response

The exact proprietary code inside the Sam's Club app is closed-source (they typically use enterprise SDKs like Scandit or proprietary vision models), but **yes, open-source technology exists to duplicate this exact experience**, and a **Pixel 8 is one of the best devices to run it on**.

Because Chrome on Android natively supports the **Shape Detection API (`BarcodeDetector`)**, your Pixel 8 can run hardware-accelerated, real-time bounding box detection directly inside a Progressive Web App (PWA) with zero third-party cloud costs or heavy WebAssembly bundles.

---

### How the Sam's Club UX Works Under the Hood

The seamless "search → bracket → snap" interaction relies on four distinct steps:

1. **Camera Stream:** Request a 1080p rear video stream (`getUserMedia`) with continuous autofocus (`focusMode: "continuous"`).
2. **Real-Time Detection:** Run a continuous frame loop using `requestAnimationFrame` or an `ImageCapture` grab.
3. **Dynamic Reticle / Bounding Box:** The detector returns the exact coordinates (`cornerPoints` and `boundingBox`) of the UPC code. An HTML5 `<canvas>` overlaid on the video draws an animated reticle/brackets around the code in real time.
4. **Haptic & Audio Feedback:** The instant a valid UPC-A / EAN-13 code is verified, trigger `navigator.vibrate([40, 30, 40])` to deliver the native checkout "beep/buzz" feel before navigating or adding to cart.

---

### Open-Source Implementation (Pixel 8 PWA)

Save this single file as `index.html`. It runs directly in Chrome on your Pixel 8:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no" />
  <title>PWA UPC Scanner</title>
  <style>
    body {
      margin: 0;
      background: #000;
      overflow: hidden;
      font-family: system-ui, sans-serif;
    }
    #viewport-container {
      position: relative;
      width: 100vw;
      height: 100vh;
    }
    video {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    canvas {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
    }
    #ui-overlay {
      position: absolute;
      bottom: 40px;
      left: 0;
      right: 0;
      text-align: center;
      color: #fff;
    }
    .badge {
      display: inline-block;
      background: rgba(0, 0, 0, 0.7);
      padding: 10px 20px;
      border-radius: 20px;
      font-size: 14px;
      letter-spacing: 0.5px;
    }
  </style>
</head>
<body>

<div id="viewport-container">
  <video id="video" playsinline muted autoplay></video>
  <canvas id="overlay"></canvas>
  <div id="ui-overlay">
    <div class="badge" id="status">Point camera at a barcode</div>
  </div>
</div>

<script>
  const video = document.getElementById('video');
  const canvas = document.getElementById('overlay');
  const ctx = canvas.getContext('2d');
  const statusEl = document.getElementById('status');

  let detector;
  let isScanning = true;

  async function init() {
    // Verify native BarcodeDetector support (available in Chrome on Pixel 8)
    if (!('BarcodeDetector' in window)) {
      statusEl.textContent = 'BarcodeDetector API not supported in this browser.';
      return;
    }

    // Limit to retail barcode types for maximum speed
    detector = new BarcodeDetector({
      formats: ['upc_a', 'upc_e', 'ean_13', 'ean_8']
    });

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1920 },
          height: { ideal: 1080 }
        }
      });
      video.srcObject = stream;
      await video.play();

      resizeCanvas();
      window.addEventListener('resize', resizeCanvas);
      requestAnimationFrame(renderLoop);
    } catch (err) {
      statusEl.textContent = 'Camera access denied: ' + err.message;
    }
  }

  function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  }

  async function renderLoop() {
    if (!isScanning) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (video.readyState === video.HAVE_ENOUGH_DATA) {
      try {
        const barcodes = await detector.detect(video);
        if (barcodes.length > 0) {
          const code = barcodes[0];
          drawBrackets(code.cornerPoints);
          onBarcodeCaptured(code.rawValue);
          return;
        }
      } catch (e) {
        console.error(e);
      }
    }
    requestAnimationFrame(renderLoop);
  }

  // Maps video frame coordinates to CSS screen coordinates and draws bounding brackets
  function drawBrackets(cornerPoints) {
    const scaleX = canvas.width / video.videoWidth;
    const scaleY = canvas.height / video.videoHeight;
    const scale = Math.max(scaleX, scaleY);

    const offsetX = (canvas.width - video.videoWidth * scale) / 2;
    const offsetY = (canvas.height - video.videoHeight * scale) / 2;

    const pts = cornerPoints.map(pt => ({
      x: pt.x * scale + offsetX,
      y: pt.y * scale + offsetY
    }));

    ctx.strokeStyle = '#00FF66';
    ctx.lineWidth = 4;
    ctx.lineJoin = 'round';

    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);
    for (let i = 1; i < pts.length; i++) {
      ctx.lineTo(pts[i].x, pts[i].y);
    }
    ctx.closePath();
    ctx.stroke();

    // Fill with slight green tint
    ctx.fillStyle = 'rgba(0, 255, 102, 0.15)';
    ctx.fill();
  }

  function onBarcodeCaptured(value) {
    isScanning = false;
    if (navigator.vibrate) navigator.vibrate(60); // Haptic feedback
    statusEl.textContent = `Captured: ${value}`;
    statusEl.style.background = '#008033';
  }

  init();
</script>
</body>
</html>

```

---

### Key Open-Source Alternatives & Fallbacks

* **Native Web Barcode Detection API (Recommended for Pixel):** Completely free, hardware-accelerated, and built straight into Android Chrome. It gives you `cornerPoints` so you can draw live tracking brackets with zero bundle bloat.
* **ZXing-JS (`@zxing/library`):** The standard open-source fallback if you ever need iOS Safari support (which doesn't yet have native `BarcodeDetector` enabled by default).
* **HTML5-QRCode:** A popular open-source wrapper built on top of ZXing and the Barcode Detection API that provides out-of-the-box UI overlays.