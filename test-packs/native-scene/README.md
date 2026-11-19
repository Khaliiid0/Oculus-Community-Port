# Native scene-input parity (developer diagnostic)

Native vertex/fragment terrain and shadow compare brightness, sun angle,
blindness and maximum blindness/darkness. Post compares shadow markers on the
left and tagged native terrain pixels on the right against its independently
uploaded inputs. Green is success, magenta is mismatch, orange is no populated
shadow samples (inconclusive). Ordinary sky/hand pixels remain unchanged.

Use an isolated copied world and non-default gamma (e.g. .37); test noon/night,
blindness/darkness effects, reload and resize. Quantized colors allow .005 error.
Negative control: add .25 to vertexInputs.r in only the native terrain vertex
shader; the right oracle must fail without changing the shadow oracle. Restore.
This proves frame-input parity, not full pack support or native scene completion.
