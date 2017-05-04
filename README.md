# MQTT Home Simulator

Simulating smart lighting in a small home using MQTT.

A simulated home consisting of a number of smart light bulbs in a number
of areas -- living room, kitchen and outside.

Example MQTT topic used to send to a lightbulb:
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
m3g/dat/Me/Home/Light/Color/0001/./Cmd/.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

And JSON message for the topic:
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
{"value":{"on":true, "h":240, "s":1, "v":1 }}
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The light bulbs make use of HSV color model. Hue ("h") goes from 0.0 to 360.0.
Saturation ("s") and brightness ("v") both go from 0.0 to 1.0.

By default, test.mosquitto.org (port 1883) is used as the MQTT broker. This
can be changed in the Settings. You can see the mapping of light bulbs to
home areas in the Settings too. The other parameters in the Settings are the
Organization and the Site. By default, they are "Me" and "Home",
respectively. In the topic above, you need to change the organization "Me"
and/or the site "Home" if you changed the Organization and/or Site
settings.
