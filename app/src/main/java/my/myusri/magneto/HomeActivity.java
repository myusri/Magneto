package my.myusri.magneto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.util.Map;
import java.util.TreeMap;

public class HomeActivity extends AppCompatActivity {
  private static final String[] locations = {
    "living1", "living2", "kitchen1", "kitchen2", "outside1", "outside2" };

  private SharedPreferences prefs;
  private Map<String, String> lightMap;
  private HomeApp app;

  public void turnOn(String id, boolean on) {
    View light = getLight(id);
    if (light == null) return;
    light.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
  }

  public void setLightColor(String id, float r, float g, float b) {
    int ir = Math.round(r*255);
    int ig = Math.round(g*255);
    int ib = Math.round(b*255);
    // RGB part must be the same for start and end color for pre-5.0 to work properly!
    int start = Color.rgb(ir, ig, ib);
    int end = Color.argb(0x60, ir, ig, ib);
    ImageView light = (ImageView) getLight(id);
    if (light == null) return;
    GradientDrawable draw = (GradientDrawable) light.getDrawable().mutate();
    draw.setColors(new int[]{start, end});
  }

  public void setLightColorHsv(String id, float hue, float sat, float val) {
    float r, g, b;
    if (sat == 0)
      r = g = b = val;
    else {
      float h = hue/60;
      int i = (int) h;
      float f = h - i;
      float p = val * (1 - sat);
      float q = val * (1 - sat * f);
      float t = val * (1 - sat * (1 - f));
      switch (i) {
        case 0 : r = val; g = t  ; b = p  ; break;
        case 1 : r = q  ; g = val; b = p  ; break;
        case 2 : r = p  ; g = val; b = t  ; break;
        case 3 : r = p  ; g = q  ; b = val; break;
        case 4 : r = t  ; g = p  ; b = val; break;
        default: r = val; g = p  ; b = q  ; break;
      }
    }
    setLightColor(id, r, g, b);
  }
  private static final int[] CONNECTED_COLORS
    = { Color.rgb(31, 255, 31), Color.argb(0xa0, 31, 255, 31)};
  private static final int[] DISCONNECTED_COLORS
    = { Color.rgb(255, 31, 31), Color.argb(0xa0, 255, 31, 31)};

  public void setConnectionIndicator(boolean connected) {
    ImageView view = (ImageView) findViewById(R.id.conn_stat);
    GradientDrawable ind = (GradientDrawable) view.getDrawable();
    ind.setColors(connected ? CONNECTED_COLORS : DISCONNECTED_COLORS);
  }

  private View getLight(String id) {
    String loc = lightMap.get(id);
    if (loc == null) return null;
    Resources res = getResources();
    int rid = res.getIdentifier(loc, "id", this.getPackageName());
    return findViewById(rid);
  }

  private void buildLightMap() {
    lightMap = new TreeMap<>();
    for (String loc: locations) {
      String id = prefs.getString(loc, null);
      if (id != null)
        lightMap.put(id, loc);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.plan_menu, menu);
    return true;
  }
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.plan_settings: {
        Intent intent = new Intent(this, SettingsActivity.class);
        this.startActivity(intent);
        break;
      }
    }
    return false;
  }
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_plan);
    Toolbar tb = (Toolbar) findViewById(R.id.plan_toolbar);
    setSupportActionBar(tb);
    tb.showOverflowMenu();
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    buildLightMap();
    app = (HomeApp) getApplication();
    app.setHomeActivity(this);
  }

  @Override
  protected void onDestroy() {
    app.setHomeActivity(null);
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    buildLightMap();
    app.checkMqttPrefs();
  }
}
