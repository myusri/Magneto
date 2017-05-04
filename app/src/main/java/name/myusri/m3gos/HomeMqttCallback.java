package name.myusri.m3gos;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

class HomeMqttCallback implements MqttCallbackExtended {
  private final static String TAG = "HomeMqttCallback";
  private String org;
  private String site;
  private IMqttAsyncClient mqtt;
  private Handler handler;

  HomeMqttCallback(Handler handler) {
    this.handler = handler;
  }

  @Override
  public void connectComplete(boolean reconnect, String url) {
    Log.i(TAG, String.format("connected. URL:%s reconnect:%b", url, reconnect));
    Message msg = handler.obtainMessage(HomeApp.MQTT_CONNECTED);
    handler.sendMessage(msg);
    try {
      String filter = String.format("m3g/dat/%s/%s/Light/+/+/+/Cmd/+", org, site);
      mqtt.subscribe(filter, 0);
    } catch (MqttException e) {
      Log.e(TAG, String.format("subscribe failed (%s): %s",  url, e.getMessage()));
    }
  }

  @Override
  public void connectionLost(Throwable cause) {
    Log.e(TAG, "connection lost");
    Message msg = handler.obtainMessage(HomeApp.MQTT_DISCONNECTED);
    handler.sendMessage(msg);
  }

  @Override
  public void messageArrived(String topic, MqttMessage message) throws Exception {
    Log.d(TAG, String.format("message received. topic:%s", topic));
    String[] pair = { topic, new String(message.getPayload()) };
    Message msg = handler.obtainMessage(HomeApp.MESSAGE_RECEIVED, pair);
    handler.sendMessage(msg);
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    for (String topic: token.getTopics()) {
      Log.d(TAG, String.format("message delivered. topic:%s", topic));
    }
  }

  HomeMqttCallback setOrg(String org) {
    this.org = org;
    return this;
  }

  HomeMqttCallback setSite(String site) {
    this.site = site;
    return this;
  }

  HomeMqttCallback setMqtt(IMqttAsyncClient mqtt) {
    this.mqtt = mqtt;
    return this;
  }
}
