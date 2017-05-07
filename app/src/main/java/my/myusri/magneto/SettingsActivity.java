package my.myusri.magneto;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsActivity
  extends AppCompatPreferenceActivity
  implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static final Pattern MQTT_URL_PAT = Pattern.compile(
    "^(?:([a-z][-+.a-z0-9]*)://)?([^:]+)(?::([0-9]+))?$", Pattern.CASE_INSENSITIVE);

  /**
   * A preference value change listener that updates the preference's summary
   * to reflect its new value.
   */
  private static final Preference.OnPreferenceChangeListener sBindListener
    = new Preference.OnPreferenceChangeListener() {
    @Override
    public boolean onPreferenceChange(Preference p, Object v) {
      String val = v.toString();
      if (p.hasKey() && p.getKey().equals("mqtt_url")) {
        Matcher m = MQTT_URL_PAT.matcher(val);
        boolean matches = m.matches();
        if (!matches) {
          Toast.makeText(p.getContext(),
            String.format("Bad MQTT URI %s", val), Toast.LENGTH_LONG)
            .show();
          return false;
        }
        String scheme = m.group(1);
        if (scheme == null) {
          val = "tcp://" + val;
          p.setSummary(val);
          return true;
        }
        if (!scheme.equalsIgnoreCase("tcp")
          && !scheme.equalsIgnoreCase("ssl")) {
          Toast.makeText(p.getContext(),
            String.format("Bad MQTT URI %s", val), Toast.LENGTH_LONG)
            .show();
          p.setSummary(val);
          return false;
        }
      }
      if (p instanceof ListPreference) {
        // For list preferences, look up the correct display value in
        // the preference's 'entries' list.
        ListPreference listPreference = (ListPreference) p;
        int index = listPreference.findIndexOfValue(val);

        // Set the summary to reflect the new value.
        p.setSummary(
          index >= 0
            ? listPreference.getEntries()[index]
            : null);

      } else {
        // For all other preferences, set the summary to the value's
        // simple string representation.
        p.setSummary(val);
      }
      return true;
    }
  };

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    if (key.equals("mqtt_url")) {
      String val = prefs.getString("mqtt_url", "");
      Matcher m = MQTT_URL_PAT.matcher(val);
      boolean matches = m.matches();
      if (!matches) return; // should have been taken care of in onPreferenceChange
      String scheme = m.group(1);
      if (scheme == null) {
        val = "tcp://" + val;
        prefs.edit().putString("mqtt_url", val).apply();
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setupActionBar();
    HomePreferenceFragment frag = new HomePreferenceFragment();
    getFragmentManager()
      .beginTransaction()
      .replace(android.R.id.content, frag)
      .commit();
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.registerOnSharedPreferenceChangeListener(this);
  }


  /**
   * Helper method to determine if the device has an extra-large screen. For
   * example, 10" tablets are extra-large.
   */
  private static boolean isXLargeTablet(Context context) {
    return (context.getResources().getConfiguration().screenLayout
      & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
  }

  /**
   * Binds a preference's summary to its value. More specifically, when the
   * preference's value is changed, its summary (line of text below the
   * preference title) is updated to reflect the value. The summary is also
   * immediately updated upon calling this method. The exact display format is
   * dependent on the type of preference.
   *
   * @see #sBindListener
   */
  private static void bindPreferenceSummaryToValue(Preference preference) {
    // Set the listener to watch for value changes.
    preference.setOnPreferenceChangeListener(sBindListener);

    // Trigger the listener immediately with the preference's
    // current value.
    sBindListener.onPreferenceChange(preference,
      PreferenceManager
        .getDefaultSharedPreferences(preference.getContext())
        .getString(preference.getKey(), ""));
  }

  /**
   * Set up the {@link android.app.ActionBar}, if the API is available.
   */
  private void setupActionBar() {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      // Show the Up button in the action bar.
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      NavUtils.navigateUpFromSameTask(this);
      return true;
    }
    return super.onMenuItemSelected(featureId, item);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean onIsMultiPane() {
    return isXLargeTablet(this);
  }

  /**
   * This fragment shows general preferences only. It is used when the
   * settingsActivity is showing a two-pane settings UI.
   */
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public static class HomePreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
      setHasOptionsMenu(true);
      bindPreferenceSummaryToValue(findPreference("mqtt_url"));
      bindPreferenceSummaryToValue(findPreference("m3g_org"));
      bindPreferenceSummaryToValue(findPreference("m3g_site"));
      bindPreferenceSummaryToValue(findPreference("living1"));
      bindPreferenceSummaryToValue(findPreference("living2"));
      bindPreferenceSummaryToValue(findPreference("kitchen1"));
      bindPreferenceSummaryToValue(findPreference("kitchen2"));
      bindPreferenceSummaryToValue(findPreference("outside1"));
      bindPreferenceSummaryToValue(findPreference("outside2"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      int id = item.getItemId();
      if (id == android.R.id.home) {
        startActivity(new Intent(getActivity(), SettingsActivity.class));
        return true;
      }
      return super.onOptionsItemSelected(item);
    }
  }
}
