import requests
import json
import numpy as np
import cv2

global ResultText
ResultText = ""
frame_len = 20
server_url = "https://2298-2001-288-4001-d889-64a9-4010-f411-de6d.ngrok-free.app"

def init_slr():
    init_url = f"{server_url}/api/init"
    response = requests.post(init_url)
    global ResultText
    ResultText = ""
    if response.status_code == 200:
        # json.loads(response.text)["message"]
        print("DONE: SLR Init")
    else:
        print("Fail: SLR Init")

def send_frame(i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15, i16, i17, i18, i19, i20):
    # frame = cv2.imdecode(np.frombuffer(frame_data, np.uint8), cv2.IMREAD_COLOR)
    global  ResultText
    
#     if len(FramesBuffer) < 20:
#         image = cv2.imdecode(np.asarray(frame_data), cv2.IMREAD_COLOR)
#         rotate = cv2.rotate(image, cv2.ROTATE_180)
#         FramesBuffer.append(rotate.tolist())
#         return False
#     else:

    inputs = [i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15, i16, i17, i18, i19, i20]

    FramesBuffer = []
    for i in inputs:
        image = cv2.imdecode(np.asarray(i), cv2.IMREAD_COLOR)
        rotate = cv2.rotate(image, cv2.ROTATE_180)
        FramesBuffer.append(rotate.tolist())

    slr_url = f"{server_url}/api/slr"
    data_to_send = {'frames': FramesBuffer}
    headers = {'Content-type': 'application/json'}
    response = requests.post(slr_url, data=json.dumps(data_to_send), headers=headers)
    # response = requests.post(slr_url, data=data_to_send)

    if response.status_code == 200:
        global ResultText
        res = json.loads(response.text)
        ResultText = res["message"]
        is_stop = res["bool"]
        print("return TEXT:", ResultText)
        return is_stop
    else:
        print("ERROR")
        return True

def get_text():
    global ResultText
    return ResultText