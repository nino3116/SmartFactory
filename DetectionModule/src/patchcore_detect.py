import torchvision
import torch
import cv2 as cv
import matplotlib.pyplot as plt

model = torch.load("patchcore_trained_model.pt", weights_only=False)
results = model.predict(
    source="dataset/apple/test/abnormal/r0_0_100.jpg", conf=0.25, save=True
)

print(results)
