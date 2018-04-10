# RoboBrains
Android application for computer vision and Arduino control

### Background
Single-board-computers like the raspberry pi do not have the performance required to run complex visual processing pipelines with low enough latency for use in real-time systems (e.g. a rover following a ball around on the ground). Additionally, finding and integrating all the hardware required for power supplies, cameras, displays, and internet are expensive and cumbersome. 

To that end, this is an android application which can utilize the high-quality cameras and powerful processors and GPUs found in modern phones. The application uses an arduino to interface with other hardware, and utilizes the OpenCV library for its computer vision. 

### Status of the project
Right now the project has a single view, which is used to test the performance of a trained LBP classifier. The view also has two buttons for recording positive and negative still images, for use in training the cascade later.

Arduino connectivity is waiting for aceptable performance to be reached by the LBP cascade.

The project also includes resources for training and testing cascades, but does not include the training images because they are too large.
