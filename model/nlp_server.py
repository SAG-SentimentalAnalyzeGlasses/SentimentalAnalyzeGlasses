from keras.models import load_model
import tensorflow_hub as hub
import numpy as np
import socket
from _thread import *
import sys
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import urllib.request
from collections import Counter
from konlpy.tag import Mecab
from sklearn.model_selection import train_test_split
from keras.preprocessing.text import Tokenizer
from keras.utils import pad_sequences

host = '192.168.0.2' # Symbolic name meaning all available interfaces
port = 8888 # Arbitrary non-privileged port


server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

print ('socket created')

try:
    server_sock.bind((host, port))
except socket.error as err:
    print('Bind Fail')
    sys.exit()

print('Socket Bind Success!')

loaded_model = load_model('model.h5', custom_objects={'KerasLayer':hub.KerasLayer})
print('model loaded')

server_sock.listen(10)
print('Socket is now listening')


def threaded(client_socket, addr): 
    print('Connected with ' + str(addr[0]) + ':' + str(addr[1])) 

    # 클라이언트가 접속을 끊을 때 까지 반복합니다. 
    while True: 
        try:
            # 데이터가 수신되면 클라이언트에 다시 전송합니다.(에코)
            data = client_socket.recv(1024)

            if not data:
                print('Disconnected with ' + str(addr[0]) + ':' + str(addr[1]))
                break

            text = data.decode('utf-8')
            predict = sentiment_predict(text)
            client_socket.send(bytes(str(predict)+"\n", 'utf-8'))
            print(addr[0] + ':' + str(addr[1]) + ', received \"' + text + '\"' + ', predict ' + str(predict))

        except ConnectionResetError as e:
            print('Disconnected with ' + str(addr[0]) + ':' + str(addr[1]))
            break
             
    client_socket.close()

def sentiment_predict(new_sentence):
    new_sentence = re.sub(r'[^ㄱ-ㅎㅏ-ㅣ가-힣 ]','', new_sentence)
    new_sentence = mecab.morphs(new_sentence) # 토큰화
    new_sentence = [word for word in new_sentence if not word in stopwords] # 불용어 제거
    encoded = tokenizer.texts_to_sequences([new_sentence]) # 정수 인코딩
    pad_new = pad_sequences(encoded, maxlen = max_len) # 패딩
    return loaded_model.predict(pad_new)

while True:
    conn, addr = server_sock.accept()
    start_new_thread(threaded, (conn, addr))
server_sock.close()