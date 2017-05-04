package name.myusri.m3gos;

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
  private HomeMqttCallback mqttCallback;
  private MqttConnectOptions mqttOpts;

  private Map<String, JSONObject> lights;
  private PlanActivity planActivity;

  private final static String TAG = "HomeApp";
  private final static String clientId = UUID.randomUUID().toString();

  @Override
  public void onCreate() {
    super.onCreate();
    lights = new TreeMap<>();
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    mqttUrl = prefs.getString("mqtt_url", "");
    org = prefs.getString("m3g_org", "");
    site = prefs.getString("m3g_site", "");
    mqttCallback = new HomeMqttCallback(new Handler(this));
    mqttOpts = new MqttConnectOptions();
    mqttOpts.setAutomaticReconnect(true);
    mqttOpts.setCleanSession(true);
    mqttOpts.setKeepAliveInterval(300);
    mqttOpts.setConnectionTimeout(5);
    establishMqtt(true);
  }

  private static void closeMqtt(final MqttAndroidClient mqtt) {
    if (mqtt == null) return;
    if (!mqtt.isConnected()) return;
    try {
      mqtt.disconnect(5000, null, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken token) {
          mqtt.close();
        }

        @Override
        public void onFailure(IMqttToken token, Throwable e) {
          Log.e(TAG, String.format("Disconnect failed: %s", e.getMessage()));
          mqtt.close();
        }
      });
    } catch (MqttException e) {
      Log.e(TAG, String.format("Disconnect failed immediately: %s", e.getMessage()));
    }
  }
  private void connectMqtt() {
    try {
      mqtt.connect(mqttOpts);
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
      Log.d(TAG, String.format("url:'%s' org:'%s' site:'%s'", mqttUrl, org, site));
      closeMqtt(mqtt);
      MemoryPersistence mem = new MemoryPersistence();
      mqtt = new MqttAndroidClient(getApplicationContext(), mqttUrl, clientId, mem);
      mqttCallback.setMqtt(mqtt).setOrg(org).setSite(site);
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

  private static Pattern LIGHT_CMD_TOPIC_PAT = Pattern.compile(
    "^m3g/dat/([^/]+)/([^/]+)/Light/([^/]+)/([^/]+)/([^/]+)/Cmd/([^/]+)$");

  private static Pattern LIGHT_ID_PAT = Pattern.compile("^Light/([^/]+)/([^/]+)$");

  private boolean setLight(String light, JSONObject val) {
    if (planActivity == null) return false;
    Matcher m = LIGHT_ID_PAT.matcher(light);
    if (!m.matches()) return false;
    String sub = m.group(1);
    try {
      boolean turnOn = false;
      if (val.has("on")) {
        turnOn = val.getBoolean("on");
      }
      if (!turnOn)
        planActivity.turnOn(light, false);
      else {
        if (!sub.equalsIgnoreCase("color"))
          planActivity.setLightColorHsv(light, 240, 0, 1);
        else{
          float h = -1, s = -1, v = -1;
          if (val.has("h")) h = (float) val.getDouble("h");
          if (val.has("s")) s = (float) val.getDouble("s");
          if (val.has("v")) v = (float) val.getDouble("v");
          if (h >= 0 && s >= 0 && v >= 0)
            planActivity.setLightColorHsv(light, h, s, v);
        }
        planActivity.turnOn(light, true);
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
          Toast.makeText(this, light, Toast.LENGTH_LONG).show();
        }
      } catch (JSONException e) {
        String out = String.format("bad JSON for %s", light);
        Log.e(TAG, out);
        Toast.makeText(this, out, Toast.LENGTH_LONG).show();
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
        if (planActivity != null) planActivity.setConnectionIndicator(true);
        break;
      case MQTT_DISCONNECTED:
        if (planActivity != null) planActivity.setConnectionIndicator(false);
        break;
      default:
        return false;
    }
    return true;
  }

  public void setPlanActivity(PlanActivity planActivity) {
    if (planActivity == this.planActivity)
      return;
    this.planActivity = planActivity;
    for (String light: lights.keySet()) {
      JSONObject val = lights.get(light);
      setLight(light, val);
    }
  }
}
