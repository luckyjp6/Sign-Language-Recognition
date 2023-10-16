import numpy as np
import cv2
import base64

i = 0
def hello_func(data):
    # decoded_data = base64.b64decode(data)
    # np_data = np.fromstring(decoded_data, np.uint8)
    # img = cv2.imdecode(np_data, cv2.IMREAD_UNCHANGED)
    # cv2.imwrite('.', img)

    # cv2.imshow("capture", img)
    # cv2.waitKey(0)
    # cv2.destroyAllWindows()
    # global i
    # i += 1
    # print("#################", i)

    # print("I AM PYTHON!!!!!!!!!!!!!!!!!!!")
    image = cv2.imdecode(np.asarray(data), cv2.IMREAD_COLOR)
    _, im_buf_arr = cv2.imencode(".png", image)
    byte_im = im_buf_arr.tobytes()

    return byte_im