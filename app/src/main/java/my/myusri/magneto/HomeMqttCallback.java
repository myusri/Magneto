package my.myusri.magneto;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

class HomeMqttCallback implements MqttCallbackExtended {
  private final static String TAG = "HomeMqttCallback";
  private final Handler handler;

  HomeMqttCallback(Handler handler) {
    this.handler = handler;
  }

  @Override
  public void connectComplete(boolean reconnect, String url) {
    Log.i(TAG, String.format("connected. URL:%s reconnect:%b", url, reconnect));
    Message msg = handler.obtainMessage(HomeApp.MQTT_CONNECTED);
    handler.sendMessage(msg);
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
  }
}
