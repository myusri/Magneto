package my.myusri.magneto;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeApp extends Application implements Handler.Callback {

  public static final int  MESSAGE_RECEIVED = 1000;
  public static final int    MQTT_CONNECTED = 1001;
  public static final int MQTT_DISCONNECTED = 1002;

  private String mqttUrl;
  private String org;
  private String site;
  private SharedPreferences prefs;

  private MqttAndroidClient mqtt;
  private IMqttToken mqttToken;
  private Handler handler;
  private MqttConnectOptions mqttOpts;

  private Map<String, JSONObject> lights;
  private HomeActivity homeActivity;

  private Toast toast;

  private final static String TAG = "HomeApp";
  private final static String clientId = UUID.randomUUID().toString();

  public void showToast(String msg) {
    if (toast == null) toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    else toast.setText(msg);
    toast.show();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    lights = new TreeMap<>();
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    mqttUrl = prefs.getString("mqtt_url", "");
    org = prefs.getString("m3g_org", "");
    site = prefs.getString("m3g_site", "");
    handler = new Handler(this);
    mqttOpts = new MqttConnectOptions();
    mqttOpts.setAutomaticReconnect(true);
    mqttOpts.setCleanSession(true);
    mqttOpts.setKeepAliveInterval(300);
    mqttOpts.setConnectionTimeout(5);
  }

  private void closeMqtt(final MqttAndroidClient mqtt) {
    if (mqtt == null) return;
    if (!mqtt.isConnected()) return;
    try {
      mqtt.setCallback(null);
      // Capture disconnect async token early. If it is the same when disconnect
      // complete, we will set the indicator accordingly. Otherwise, we will not
      // touch the indicator. This happens when a new connection is successfully
      // established before this disconnection.
      mqttToken = mqtt.disconnect(5000, null, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken token) {
          Log.i(TAG, String.format("disconnected from %s", mqtt.getServerURI()));
          mqtt.close();
          if (token == mqttToken && homeActivity != null)
            homeActivity.setConnectionIndicator(false);
        }

        @Override
        public void onFailure(IMqttToken token, Throwable e) {
          Log.e(TAG, String.format("disconnect failed: %s", e.getMessage()));
        }
      });
    } catch (MqttException e) {
      Log.e(TAG, String.format("Disconnect failed immediately: %s", e.getMessage()));
    }
  }
  private void connectMqtt() {
    try {
      mqtt.connect(mqttOpts, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken token) {
          // capture async token only when connection is successful. This will
          // indicate to any current disconnection that it is not supposed to change
          // the indicator.
          mqttToken = token;
        }
        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        }
      });
    } catch (MqttException e) {
      Log.e(TAG, String.format("connect failed (%s):%s", mqttUrl, e.getMessage()));
    }
  }
  private void establishMqtt(boolean changed) {
    if (mqtt != null && !changed) {
      if (!mqtt.isConnected())
        connectMqtt();
      return;
    }
    if (!mqttUrl.isEmpty() && !org.isEmpty() && !site.isEmpty()) {
      closeMqtt(mqtt);
      MemoryPersistence mem = new MemoryPersistence();
      Log.d(TAG, String.format("connecting to %s...", mqttUrl));
      mqtt = new MqttAndroidClient(getApplicationContext(), mqttUrl, clientId, mem);
      HomeMqttCallback mqttCallback = new HomeMqttCallback(handler);
      mqtt.setCallback(mqttCallback);
      connectMqtt();
    }
  }

  public void checkMqttPrefs() {
    boolean changed = false;
    String p = prefs.getString("mqtt_url", "");
    if (!p.equals(mqttUrl)) {
      mqttUrl = p;
      changed = true;
    }
    p = prefs.getString("m3g_org", "");
    if (!p.equals(org)) {
      org = p;
      changed = true;
    }
    p = prefs.getString("m3g_site", "");
    if (!p.equals(site)) {
      site = p;
      changed = true;
    }
    establishMqtt(changed);
  }

  private static final Pattern LIGHT_CMD_TOPIC_PAT = Pattern.compile(
    "^m3g/dat/([^/]+)/([^/]+)/Light/([^/]+)/([^/]+)/([^/]+)/Cmd/([^/]+)$");

  private static final Pattern LIGHT_ID_PAT = Pattern.compile("^Light/([^/]+)/([^/]+)$");

  private boolean setLight(String light, JSONObject val) {
    if (homeActivity == null) return false;
    Matcher m = LIGHT_ID_PAT.matcher(light);
    if (!m.matches()) return false;
    String sub = m.group(1);
    try {
      boolean turnOn = false;
      if (val.has("on")) {
        turnOn = val.getBoolean("on");
      }
      if (!turnOn)
        homeActivity.turnOn(light, false);
      else {
        if (!sub.equalsIgnoreCase("color"))
          homeActivity.setLightColorHsv(light, 240, 0, 1);
        else{
          float h = -1, s = -1, v = -1;
          if (val.has("h")) h = (float) val.getDouble("h");
          if (val.has("s")) s = (float) val.getDouble("s");
          if (val.has("v")) v = (float) val.getDouble("v");
          if (h >= 0 && s >= 0 && v >= 0)
            homeActivity.setLightColorHsv(light, h, s, v);
        }
        homeActivity.turnOn(light, true);
      }
      return true;
    } catch(JSONException e) {
      Log.e(TAG, String.format("can't set light %s:%s", light, e.getMessage()));
      return false;
    }
  }

  /**
   * Handle light command MQTT message. Example topic:
   * <p><code>m3g/dat/Me/Home/Light/Basic/0006/./Cmd/.</code>
   * <p>Example JSON message:
   * <p><code>{"value":{"on":true, "h":240, "s":1, "v":1 }}</code>
   * @param pair topic and message string array
   */
  private void handleMqttMessage(String[] pair) {
    if (pair == null || pair.length != 2) return;
    String topic = pair[0];
    String msg = pair[1];
    Matcher m = LIGHT_CMD_TOPIC_PAT.matcher(topic);
    if (m.matches()) {
      String sub = m.group(3);
      String id = m.group(4);
      String light = String.format("Light/%s/%s", sub, id);
      try {
        JSONObject cmd = new JSONObject(msg);
        JSONObject val = cmd.getJSONObject("value");
        if (setLight(light, val)) {
          lights.put(light, val);
          showToast(light);
        }
      } catch (JSONException e) {
        String out = String.format("bad JSON for %s", light);
        Log.e(TAG, out);
        showToast(out);
      }
    } else {
      Log.e(TAG, String.format("unknown topic:%s", topic));
    }
  }
  @Override
  public boolean handleMessage(Message message) {
    switch (message.what) {
      case MESSAGE_RECEIVED:
        handleMqttMessage((String[]) message.obj);
        break;
      case MQTT_CONNECTED:
        if (homeActivity != null)
          homeActivity.setConnectionIndicator(true);
        try {
          String filter = String.format("m3g/dat/%s/%s/Light/+/+/+/Cmd/+", org, site);
          mqtt.subscribe(filter, 0);
        } catch (MqttException e) {
          Log.e(TAG, String.format("subscribe failed: %s",  e.getMessage()));
        }
        break;
      case MQTT_DISCONNECTED:
        if (homeActivity != null)
          homeActivity.setConnectionIndicator(false);
        break;
      default:
        return false;
    }
    return true;
  }

  public void setHomeActivity(HomeActivity homeActivity) {
    if (homeActivity == this.homeActivity) return;
    this.homeActivity = homeActivity;
    if (homeActivity == null) return;
    if (mqtt != null)
      homeActivity.setConnectionIndicator(mqtt.isConnected());
    for (String light: lights.keySet()) {
      JSONObject val = lights.get(light);
      setLight(light, val);
    }
  }
}
