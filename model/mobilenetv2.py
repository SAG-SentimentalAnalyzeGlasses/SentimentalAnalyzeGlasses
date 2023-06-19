import numpy as np
import pandas as pd
import tensorflow as tf
import os
import cv2
import matplotlib.pyplot as plt
import matplotlib as mpl

mpl.style.use("seaborn-darkgrid")

from tqdm import tqdm

files = os.listdir("./MMAFEDB/train/")

image_array=[]
label_array=[]
path = "./MMAFEDB/train/"
for i in range(len(files)):
    file_sub=os.listdir(path+files[i])

    for k in tqdm(range(len(file_sub))):
        img = cv2.imread(path+files[i]+"/"+file_sub[k])
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        image_array.append(img)
        label_array.append([files[i]])

a,b=np.unique(label_array, return_counts="True")

import gc
gc.collect()

image_array=np.array(image_array)/255.0
label_array=np.array(label_array)

from sklearn.preprocessing import MultiLabelBinarizer
mlb = MultiLabelBinarizer()
label_array = mlb.fit_transform(label_array)

from sklearn.model_selection import train_test_split
image_array,X_test,Y_train,Y_test = train_test_split(image_array, label_array, test_size=0.1)


gc.collect()


from keras import layers, callbacks, utils, applications, optimizers
from keras.models import Sequential, Model, load_model

model=Sequential()
pretrained_model = applications.MobileNetV2(input_shape=(48, 48, 3), include_top=False, weights="imagenet")
pretrained_model.trainable=True
model.add(pretrained_model)
model.add(layers.GlobalAveragePooling2D())
model.add(layers.Dropout(0.3))
model.add(layers.Dense(len(mlb.classes_), activation='softmax'))
print(model.summary())


from keras.optimizers import Adam
model.compile(optimizer=Adam(0.0001),loss="categorical_crossentropy", metrics=["accuracy"])
ckp_path="trained_model/model"
model_checkpoint=tf.keras.callbacks.ModelCheckpoint(filepath=ckp_path, monitor="val_accuracy", save_best_only=True, save_weights_only=True, model="auto")

reduce_lr=tf.keras.callbacks.ReduceLROnPlateau(factor=0.9, monitor="val_accuracy", model="auto", cooldown=0, patience=5, verbose=1, min_lr=1e-6)


EPOCHS=10
BATCH_SIZE=64
history = model.fit(image_array, Y_train, validation_data=(X_test, Y_test), batch_size=BATCH_SIZE, epochs=EPOCHS, callbacks=[model_checkpoint, reduce_lr])
# history = model.fit_generator(image_array, Y_train, validation_data=(X_test, Y_test), batch_size=BATCH_SIZE, epochs=EPOCHS, callbacks=[model_checkpoint, reduce_lr])


model.load_weights(ckp_path)


prediction_val = model.predict(X_test,batch_size=BATCH_SIZE)

print(prediction_val[:10])
print(Y_test[:10])

prediction_val = model.predict(X_test,batch_size=BATCH_SIZE)

converter=tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model=converter.convert()

with open("model.tflite", "wb") as f:
    f.write(tflite_model)