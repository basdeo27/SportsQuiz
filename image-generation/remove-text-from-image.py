import argparse
from pathlib import Path
import tempfile
import urllib.parse
import urllib.request
import easyocr
import cv2
import numpy as np

def remove_text_general(image_path, output_path):
    # 1. Load the image
    img = cv2.imread(image_path)
    if img is None:
        print("Could not read the image.")
        return

    # 2. Initialize the OCR reader (supports 80+ languages)
    reader = easyocr.Reader(['en'])

    # 3. Detect text
    # Result returns: [[box coordinates], "text", confidence]
    results = reader.readtext(image_path)

    # 4. Create a mask (a black image where we will paint the text areas white)
    mask = np.zeros(img.shape[:2], dtype="uint8")

    for (bbox, text, prob) in results:
        # Extract coordinates
        # bbox is [[x,y] top-left, [x,y] top-right, [x,y] bottom-right, [x,y] bottom-left]
        top_left = tuple(map(int, bbox[0]))
        bottom_right = tuple(map(int, bbox[2]))

        # Draw a white rectangle on the mask over the text area
        cv2.rectangle(mask, top_left, bottom_right, 255, -1)

    # 5. Inpaint the image
    # This looks at the pixels surrounding the mask and fills it in
    # INPAINT_TELEA or INPAINT_NS work well for most logos/graphics
    result = cv2.inpaint(img, mask, inpaintRadius=3, flags=cv2.INPAINT_TELEA)

    # 6. Save the final PNG
    cv2.imwrite(output_path, result)
    print(f"Processed image saved as: {output_path}")

# def remove_text_general(image_path, output_path):
#     img = cv2.imread(image_path, cv2.IMREAD_UNCHANGED)
#     if img is None:
#         print("Could not read the image.")
#         return
#
#     if img.shape[2] == 4:
#         color_img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
#         alpha_channel = img[:, :, 3]
#     else:
#         color_img = img
#         alpha_channel = None
#
#     reader = easyocr.Reader(["en"])
#     results = reader.readtext(color_img)
#
#     mask = np.zeros(color_img.shape[:2], dtype="uint8")
#     for (bbox, text, prob) in results:
#         polygon = np.array(bbox, dtype=np.int32)
#         cv2.fillPoly(mask, [polygon], 255)
#
#     if np.any(mask):
#         kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
#         mask = cv2.dilate(mask, kernel, iterations=1)
#
#     cleaned_color = cv2.inpaint(color_img, mask, inpaintRadius=5, flags=cv2.INPAINT_NS)
#
#     if alpha_channel is not None:
#         final_img = cv2.merge(
#             [cleaned_color[:, :, 0], cleaned_color[:, :, 1], cleaned_color[:, :, 2], alpha_channel]
#         )
#     else:
#         final_img = cleaned_color
#
#     cv2.imwrite(output_path, final_img)
#     print(f"Processed image saved as: {output_path}")

# def remove_text_general(url, output_name="cleaned_image.png"):
#     # 1. Download and decode image
#     resp = urllib.request.urlopen(url)
#     img_array = np.asarray(bytearray(resp.read()), dtype="uint8")
#     img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
#
#     if img is None: return
#
#     # 2. Initialize the OCR reader (supports 80+ languages)
#     reader = easyocr.Reader(['en'])
#
#     # 3. Detect text locations
#     # result contains bounding boxes: [[x,y] coordinates, 'text', confidence]
#     results = reader.readtext(img)
#
#     # 4. Create a mask based on detected boxes
#     mask = np.zeros(img.shape[:2], dtype="uint8")
#     for (bbox, text, prob) in results:
#         # Define the corners of the box
#         (tl, tr, br, bl) = bbox
#         pts = np.array([tl, tr, br, bl], np.int32)
#
#         # Fill the detected text area in the mask
#         cv2.fillPoly(mask, [pts], 255)
#
#     # 5. Dilate the mask slightly
#     # This ensures we catch the edges/shadows of the letters
#     kernel = np.ones((5,5), np.uint8)
#     mask = cv2.dilate(mask, kernel, iterations=2)
#
#     # 6. Inpaint
#     final_img = cv2.inpaint(img, mask, inpaintRadius=7, flags=cv2.INPAINT_TELEA)
#
#     # 7. Save
#     cv2.imwrite(output_name, final_img)
#     print(f"Processed {len(results)} text regions. Saved as {output_name}")

def download_image(url):
    parsed = urllib.parse.urlparse(url)
    if not parsed.scheme or not parsed.netloc:
        raise ValueError("Invalid URL provided.")
    suffix = Path(parsed.path).suffix or ".png"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp_file:
        with urllib.request.urlopen(url) as response:
            tmp_file.write(response.read())
        return tmp_file.name

def build_output_path(hostname, input_path):
    input_stem = Path(input_path).stem or "image"
    safe_host = hostname.replace("/", "_").replace("\\", "_")
    filename = f"{safe_host}-{input_stem}.png"
    return Path.home() / "Desktop" / filename


def main():
    parser = argparse.ArgumentParser(description="Remove text from an image.")
    parser.add_argument("image_path", help="Path or URL to the input image file")
    args = parser.parse_args()

    input_path = args.image_path
    hostname = "local"
    if input_path.startswith("http://") or input_path.startswith("https://"):
        hostname = urllib.parse.urlparse(input_path).netloc or "remote"
        input_path = download_image(input_path)

    output_path = build_output_path(hostname, input_path)
    remove_text_general(input_path, str(output_path))


if __name__ == "__main__":
    main()
