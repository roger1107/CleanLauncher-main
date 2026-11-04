package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

public class MainActivity extends Activity implements NowPlaying.Listener {


    private static final String MAPS = "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private NowPlaying nowPlaying;

    private TextView greeting, weatherTemp, weatherDesc, weatherCity, weatherExtra;
    private ImageView weatherIcon;
    private GradientDrawable weatherBg;
    private ImageView car, carLights;
    private boolean hasLightsImage;
    private TextView oilInfo;
    private boolean createdDark;
    private String createdProfile;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private TextView fmBand, fmFreq;
    private TextView outsideTemp;
    private SpeedGauge speedGauge;
    private LiveMapView liveMap;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;
    private TextView docsInfo, tripKm, tripTime, tripAvg, tripMax, phoneSub, rainNote, soundChip;
    private GradientDrawable docsBg, radioPowerBg;
    private ImageView radioPower;
    private View rootView, weatherCard, drivingPanel;
    private TextView drivingSpeed, drivingLimit, drivingTrip, drivingTime, drivingAvg;
    private final SoundClient sound = new SoundClient();
    private int createdPart;
    private boolean driving;
    private int fastPolls, slowPolls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Left shortcut bar | top strip (clock, date, oil, outside temperature) + three columns:
        // greeting/weather, map + music, and the shortcut tiles with the speedometer.
        LinearLayout root = new LinearLayout(this);
        root.setBackground(Ui.background(this));
        root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(80), -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(8), dp(16), dp(14));
        content.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout cards = new LinearLayout(this);
        cards.addView(buildLeftSlot(), weight(1.3f, 0));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(buildMapCard(), vweight(0.9f, 0));
        middle.addView(buildMusicCard(), vweight(1.15f, dp(12)));
        cards.addView(middle, weight(1f, dp(12)));

        cards.addView(buildTiles(), weight(0.85f, dp(12)));
        LinearLayout.LayoutParams clp0 = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp0.topMargin = dp(6);
        content.addView(cards, clp0);

        // App shortcuts along the bottom (YouTube, Netflix, Maps...), only the ones installed.
        LinearLayout.LayoutParams dock = new LinearLayout.LayoutParams(-1, dp(68));
        dock.topMargin = dp(10);
        content.addView(DockBar.build(this, this::openMaps, this::openRadio,
                () -> startActivity(new Intent(this, AppsActivity.class))), dock);

        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);
        rootView = root;

        radio.bind(this);
        sound.bind(this, () -> { });
        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdProfile = profileStamp();
        createdPart = Ui.dayPart();
        // Shared installs: ask for name, car photo and permissions on first start.
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) startService(new Intent(this, VolumeOverlay.class));
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
    }

    // ---------- Greeting + weather, or the big driving panel while moving ----------

    private View buildLeftSlot() {
        FrameLayout slot = new FrameLayout(this);
        weatherCard = buildWeatherCard();
        slot.addView(weatherCard, new FrameLayout.LayoutParams(-1, -1));
        drivingPanel = buildDrivingPanel();
        drivingPanel.setVisibility(View.GONE);
        slot.addView(drivingPanel, new FrameLayout.LayoutParams(-1, -1));
        return slot;
    }

    private View buildDrivingPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        p.setPadding(dp(26), dp(22), dp(26), dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.glassTint(this));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x447FD8FF);
        p.setBackground(bg);

        TextView tag = Ui.text(this, "MODO CONDUCCIÓN", 12, 0xB3FFFFFF);
        tag.setLetterSpacing(0.12f);
        p.addView(tag);

        drivingSpeed = Ui.text(this, "0", 120, Color.WHITE);
        drivingSpeed.setTypeface(Typeface.DEFAULT_BOLD);
        drivingSpeed.setIncludeFontPadding(false);
        p.addView(drivingSpeed, new LinearLayout.LayoutParams(-1, 0, 1f));
        p.addView(Ui.text(this, "km/h", 20, 0xCCFFFFFF));
        drivingLimit = Ui.text(this, "", 15, 0xFFFFC94D);
        drivingLimit.setPadding(0, dp(4), 0, 0);
        p.addView(drivingLimit);

        LinearLayout stats = new LinearLayout(this);
        drivingTrip = stat(stats, "Viaje");
        drivingTime = stat(stats, "Manejando");
        drivingAvg = stat(stats, "Promedio");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(16);
        p.addView(stats, slp);
        return p;
    }

    private TextView stat(LinearLayout row, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView value = Ui.text(this, "--", 18, Color.WHITE);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(value);
        col.addView(Ui.text(this, label, 12, 0xB3FFFFFF));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return value;
    }

    private void setDriving(boolean on) {
        driving = on;
        View in = on ? drivingPanel : weatherCard;
        View out = on ? weatherCard : drivingPanel;
        out.setVisibility(View.GONE);
        in.setAlpha(0f);
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).setDuration(400).start();
    }

    /** Enter after ~3 s at 10 km/h or more; leave after ~15 s below 3 km/h (a red light does not flip it). */
    private void updateDriving(float v) {
        boolean enabled = Profile.drivingModeEnabled(this);
        if (v >= 10f) {
            fastPolls++;
            slowPolls = 0;
        } else if (v >= 0f && v < 3f) {
            slowPolls++;
            fastPolls = 0;
        }
        if (!driving && enabled && fastPolls >= 3) setDriving(true);
        else if (driving && (!enabled || slowPolls >= 15)) setDriving(false);
        if (!driving) return;

        int limit = Odometer.speedLimit(this);
        drivingSpeed.setText(v < 0 ? "--" : String.valueOf(Math.round(v)));
        drivingSpeed.setTextColor(limit > 0 && v > limit ? 0xFFFF6B81 : Color.WHITE);
        drivingLimit.setText(limit > 0 ? "Límite " + limit + " km/h" : "");
        TripStats t = Odometer.trip(this);
        drivingTrip.setText(TripStats.formatKm(t.meters()));
        drivingTime.setText(TripStats.formatDuration(t.sessionMs()) + " h");
        drivingAvg.setText(Math.round(t.avgKmh()) + " km/h");
    }

    // ---------- Greeting + weather ----------

    private View buildWeatherCard() {
        FrameLayout frame = new FrameLayout(this);
        weatherBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Ui.glassTint(this));
        weatherBg.setCornerRadius(dp(18));
        weatherBg.setStroke(dp(1), 0x6678B4FF);
        frame.setBackground(weatherBg);
        frame.setClipToOutline(true);

        // The bundled scene photo fills the whole card; a photo the user picked keeps the small centred car.
        android.graphics.Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false);
        boolean scene = own == null && bundled != 0;
        if (scene) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageResource(bundled);
            if (Ui.isDark(this)) photo.setColorFilter(0xFF8E98B4, android.graphics.PorterDuff.Mode.MULTIPLY);
            frame.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            // Dark gradients top and bottom so the greeting and the weather stay readable over the photo.
            View scrim = new View(this);
            scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xB3050B1C, 0x00050B1C, 0x00050B1C, 0xF2050B1C}));
            frame.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(22));
        frame.addView(card, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView hello = left(Ui.text(this, Profile.hello(this), 32, Color.WHITE));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        names.addView(hello);
        greeting = left(Ui.text(this, "", 16, 0xCCFFFFFF));
        greeting.setPadding(0, dp(4), 0, 0);
        names.addView(greeting);
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        // Round car button: oil, papers and the trip.
        head.addView(Ui.iconButton(this, R.drawable.ic_car, 44, false, this::showCarMenu),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(head);

        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        if (scene) {
            // Empty space over the photo; tapping it opens the setup screen like the old car picture did.
            View space = new View(this);
            space.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
            card.addView(space, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            FrameLayout carBox = new FrameLayout(this);
            if (own != null) {
                car.setImageBitmap(own);
            } else {
                car.setImageResource(R.drawable.ic_car);
                car.setColorFilter(0x40FFFFFF);
            }
            carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
            carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
            carBox.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
            clp.setMargins(dp(30), dp(8), dp(30), dp(8));
            card.addView(carBox, clp);
        }

        // Compact weather row: icon + temperature + description, leaving room for the car.
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        weatherTemp = left(Ui.text(this, "--°", 50, Color.WHITE));
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, dp(12), 0);
        now.addView(weatherTemp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 16, Color.WHITE));
        weatherDesc.setSingleLine(false);
        weatherDesc.setMaxLines(2);
        texts.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "", 13, 0xB3FFFFFF));
        weatherCity.setPadding(0, dp(2), 0, 0);
        texts.addView(weatherCity);
        now.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(now);

        rainNote = left(Ui.text(this, "", 14, 0xFF9FE0FF));
        rainNote.setPadding(0, dp(8), 0, 0);
        rainNote.setVisibility(View.GONE);
        card.addView(rainNote);

        weatherExtra = left(Ui.text(this, "", 14, 0xE6FFFFFF));
        weatherExtra.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x26FFFFFF);
        chip.setCornerRadius(dp(20));
        weatherExtra.setBackground(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        card.addView(weatherExtra, lp);

        if (scene) {
            // The photo covers the card's own stroke, so draw the border on top.
            View border = new View(this);
            GradientDrawable bd = new GradientDrawable();
            bd.setCornerRadius(dp(18));
            bd.setStroke(dp(1), 0x8878B4FF);
            border.setBackground(bd);
            frame.addView(border, new FrameLayout.LayoutParams(-1, -1));
        }
        Ui.pressable(frame, 0.98f);
        frame.setOnClickListener(v -> fetchWeather(true));
        return frame;
    }

    private void updateGreeting() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        greeting.setText((h < 12 ? "Buenos días" : DayParts.isNight(h, c.get(Calendar.MINUTE)) ? "Buenas noches" : "Buenas tardes")
                + " · Bienvenido");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        weatherTemp.setText(w.temp + "°");
        outsideTemp.setText(w.temp + "°C");
        weatherDesc.setText(w.description());
        weatherCity.setText(w.city.isEmpty() ? "Sensación " + w.feelsLike + "°"
                : w.city + " · Sensación " + w.feelsLike + "°");
        weatherExtra.setText("↑ " + w.max + "°   ↓ " + w.min + "°   💧 " + w.humidity + "%   " + w.wind + " km/h");
        String rain = w.rainNote();
        rainNote.setText("🌧 " + rain);
        rainNote.setVisibility(rain.isEmpty() ? View.GONE : View.VISIBLE);
        weatherBg.setColors(Ui.glassTint(this));
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) recreate();
            if (Ui.dayPart() != createdPart) { // the tint of the light background follows the day
                createdPart = Ui.dayPart();
                rootView.setBackground(Ui.background(MainActivity.this));
            }
            showCarLights(carLightsOn());
            updateGreeting();
            updateDocs();
            updatePhone();
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            android.util.Log.d("CarLights", "ILL broadcast extras=" + i.getExtras());
            showCarLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0 || nightByClock());
            // Auto theme follows the car's lights; give the kernel a moment to store the state.
            handler.postDelayed(() -> { if (Ui.isDark(MainActivity.this) != createdDark) recreate(); }, 1500);
        }
    };

    /** Night by the clock (18:30 to 6:00): headlights glow even without the ILL wire. */
    private boolean nightByClock() {
        return Ui.dayPart() == DayParts.NIGHT;
    }

    /** The kernel keeps the light state as bit 2 of mcu_no_source_device_state; or it is night by the clock. */
    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0 || nightByClock();
    }

    private void showCarLights(boolean on) {
        // Cross-fade between the "lights off" and "lights on" photos.
        if (!hasLightsImage) return;
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    private void updateOil() {
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Top strip ----------

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(4), 0);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(30);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        bar.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(15);
        date.setTextColor(0xCCFFFFFF);
        date.setPadding(dp(16), 0, 0, 0);
        bar.addView(date);

        // Oil-change status pill; opens the oil screen.
        oilInfo = Ui.text(this, "", 13, Color.WHITE);
        oilInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.leftMargin = dp(16);
        bar.addView(oilInfo, olp);

        // Papers (insurance, registration, inspection) when one expires within 30 days.
        docsInfo = Ui.text(this, "", 13, Color.WHITE);
        docsInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        docsBg = new GradientDrawable();
        docsBg.setCornerRadius(dp(14));
        docsInfo.setBackground(docsBg);
        Ui.pressable(docsInfo, 0.92f);
        docsInfo.setOnClickListener(v -> startActivity(new Intent(this, DocsActivity.class)));
        docsInfo.setVisibility(View.GONE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(10);
        bar.addView(docsInfo, dlp);

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Outside temperature (from the weather service, not a car sensor).
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setGravity(Gravity.END);
        out.addView(Ui.text(this, "Exterior", 11, 0xB3FFFFFF));
        outsideTemp = Ui.text(this, "--°C", 18, Color.WHITE);
        outsideTemp.setTypeface(Typeface.DEFAULT_BOLD);
        out.addView(outsideTemp);
        bar.addView(out);
        return bar;
    }

    // ---------- Map card (live OpenStreetMap that follows the GPS; tap opens the navigation app) ----------

    private View buildMapCard() {
        boolean dark = Ui.isDark(this);
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF0B1220 : 0xFF0E2350);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), dark ? 0x2278B4FF : 0x6678B4FF);
        card.setBackground(bg);
        card.setClipToOutline(true);
        liveMap = new LiveMapView(this);
        card.addView(liveMap, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xCC0B1030}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout label = new LinearLayout(this);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(14), 0, dp(14), dp(12));
        ImageView compass = new ImageView(this);
        compass.setImageResource(R.drawable.ic_compass);
        compass.setColorFilter(Color.WHITE);
        compass.setPadding(dp(8), dp(8), dp(8), dp(8));
        compass.setBackground(Ui.circle(0x40FFFFFF));
        label.addView(compass, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        TextView title = left(Ui.text(this, "Navegación", 16, Color.WHITE));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(title);
        texts.addView(left(Ui.text(this, "Ir a destino", 12, 0xCCFFFFFF)));
        label.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(label, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> {
            if (hasLocationPermission()) openMaps();
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        });
        return card;
    }

    // ---------- Shortcut tiles + speedometer ----------

    private View buildTiles() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // Phone: shows the connected phone; with favourite contacts a tap offers to call them.
        LinearLayout phone = tile(R.drawable.ic_call, 0xFF3DDC97);
        phoneSub = tileSub("Conectar dispositivos");
        tileTexts(phone, tileTitle("Teléfono"), phoneSub);
        phone.setOnClickListener(v -> onPhoneTap());
        col.addView(phone, vweight(1f, 0));

        // Radio: the round power button turns it off/on; previous/next change the station.
        LinearLayout fm = tile(R.drawable.ic_power, Color.WHITE);
        radioPower = (ImageView) fm.getChildAt(0);
        radioPower.setPadding(dp(7), dp(7), dp(7), dp(7));
        radioPowerBg = new GradientDrawable();
        radioPowerBg.setShape(GradientDrawable.OVAL);
        radioPowerBg.setColor(0xFFFF9F5A);
        radioPower.setBackground(radioPowerBg);
        Ui.pressable(radioPower, 0.88f);
        radioPower.setOnClickListener(v -> toggleRadioPower());
        fmBand = tileTitle("FM Radio");
        fmFreq = tileSub("Sintonizar");
        tileTexts(fm, fmBand, fmFreq);
        fm.addView(Ui.iconButton(this, R.drawable.ic_prev, 34, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(34), dp(34));
        nlp.leftMargin = dp(6);
        fm.addView(Ui.iconButton(this, R.drawable.ic_next, 34, false, () -> stationStep(true)), nlp);
        fm.setOnClickListener(v -> openRadio());
        col.addView(fm, vweight(1f, dp(10)));

        LinearLayout auto = tile(R.drawable.ic_nav, 0xFF4F9BFF);
        tileTexts(auto, tileTitle("Android Auto"), tileSub("Conectar"));
        auto.setOnClickListener(v -> openAndroidAuto());
        col.addView(auto, vweight(1f, dp(10)));

        // Speedometer (tap: speed alert limit) + trip counters (tap: details and reset).
        LinearLayout speed = new LinearLayout(this);
        speed.setGravity(Gravity.CENTER_VERTICAL);
        speed.setBackground(Ui.card(this));
        speedGauge = new SpeedGauge(this);
        speedGauge.setLimit(Odometer.speedLimit(this));
        speedGauge.setOnClickListener(v -> {
            int limit = Odometer.nextSpeedLimit(this);
            speedGauge.setLimit(limit);
            toast(limit == 0 ? "Alerta de velocidad apagada" : "Alerta de velocidad: " + limit + " km/h");
        });
        speed.addView(speedGauge, new LinearLayout.LayoutParams(0, -1, 1.15f));

        LinearLayout trip = new LinearLayout(this);
        trip.setOrientation(LinearLayout.VERTICAL);
        trip.setGravity(Gravity.CENTER_VERTICAL);
        trip.setPadding(dp(2), dp(8), dp(10), dp(8));
        TextView tripLabel = left(Ui.text(this, "VIAJE", 10, 0xB3FFFFFF));
        tripLabel.setLetterSpacing(0.12f);
        trip.addView(tripLabel);
        tripKm = left(Ui.text(this, "0,0 km", 17, Color.WHITE));
        tripKm.setTypeface(Typeface.DEFAULT_BOLD);
        trip.addView(tripKm);
        tripTime = left(Ui.text(this, "0:00 h", 13, 0xE6FFFFFF));
        trip.addView(tripTime);
        tripAvg = left(Ui.text(this, "", 11, 0xB3FFFFFF));
        trip.addView(tripAvg);
        tripMax = left(Ui.text(this, "", 11, 0xB3FFFFFF));
        trip.addView(tripMax);
        Ui.pressable(trip, 0.95f);
        trip.setOnClickListener(v -> showTripDialog());
        speed.addView(trip, new LinearLayout.LayoutParams(0, -1, 0.85f));
        col.addView(speed, vweight(1.7f, dp(10)));
        return col;
    }

    private LinearLayout tile(int icon, int tint) {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), 0, dp(12), 0);
        t.setBackground(Ui.card(this));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(tint);
        iv.setPadding(dp(7), dp(7), dp(7), dp(7));
        iv.setBackground(Ui.circle((tint & 0x00FFFFFF) | 0x40000000));
        t.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38)));
        Ui.pressable(t, 0.97f);
        return t;
    }

    private void tileTexts(LinearLayout tile, TextView title, TextView sub) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(6), 0);
        col.addView(title);
        col.addView(sub);
        tile.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private TextView tileTitle(String s) {
        TextView tv = left(Ui.text(this, s, 16, Color.WHITE));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView tileSub(String s) {
        return left(Ui.text(this, s, 12, 0xB3FFFFFF));
    }

    private void openAndroidAuto() {
        Intent aa = getPackageManager().getLaunchIntentForPackage("com.google.android.projection.gearhead");
        if (aa != null && safeStart(aa)) return;
        if (launchByKeyword("android auto", "androidauto", "carlink", "zlink", "autokit")) return;
        toast("No se encontró Android Auto en este radio");
    }

    // ---------- Radio actions ----------

    private void stationStep(boolean next) {
        if (!Profile.isNwd(this)) {
            openRadio();
            return;
        }
        if (!Ui.radioIsSource(this)) {
            Ui.selectRadio(this, radio); // first tap from Bluetooth: bring the radio back
            handler.postDelayed(this::updateRadio, 2500);
            return;
        }
        radio.preset(next);
        handler.postDelayed(this::updateRadio, 300);
    }

    private void updateRadio() {
        radio.currentAsync(st -> {
            if (isDestroyed()) return;
            boolean on = Ui.radioIsSource(this);
            radioPowerBg.setColor(on ? 0xFFFF9F5A : 0x33FFFFFF);
            if (st == null) return;
            fmBand.setText(st.psName.isEmpty() ? "Radio " + st.bandLabel() : st.bandLabel() + " · " + st.psName);
            fmFreq.setText(on ? st.frequencyLabel() + (st.isAm() ? " kHz" : " MHz") : "Apagada");
        });
    }

    /**
     * Radio off: hand the audio path to the local-music source (idle, so silent); on: bring the radio
     * back. Logged under RadioPower so the result can be checked with `adb logcat -s RadioPower`.
     */
    private void toggleRadioPower() {
        if (!Profile.isNwd(this)) {
            openRadio();
            return;
        }
        if (Ui.radioIsSource(this)) {
            android.util.Log.d("RadioPower", "radio off -> source MUSIC");
            Ui.requestSource(this, Ui.SOURCE_MUSIC);
        } else {
            android.util.Log.d("RadioPower", "radio on");
            Ui.selectRadio(this, radio);
        }
        handler.postDelayed(this::updateRadio, 1200);
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            handler.postDelayed(this, 1000);
        }
    };

    /** GPS speed and map position once a second; the speedometer shows "--" without a recent fix. */
    private final Runnable speedPoll = new Runnable() {
        public void run() {
            long now = SystemClock.elapsedRealtime();
            long at = Odometer.speedAtMs;
            float v = at == 0 || now - at > 5000 ? -1f
                    : Odometer.speedKmh < 2f ? 0f : Odometer.speedKmh;
            speedGauge.setSpeed(v);
            updateTrip();
            updateDriving(v);
            long fixAt = Odometer.fixAtMs;
            if (fixAt != 0 && now - fixAt < 30000) {
                liveMap.setPosition(Odometer.lat, Odometer.lon, Odometer.bearing, v);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void updateTrip() {
        TripStats t = Odometer.trip(this);
        tripKm.setText(TripStats.formatKm(t.meters()));
        tripTime.setText(TripStats.formatDuration(t.movingMs()) + " h");
        tripAvg.setText("prom " + Math.round(t.avgKmh()) + " km/h");
        tripMax.setText("máx " + Math.round(t.maxKmh()) + " km/h");
    }

    private void showTripDialog() {
        TripStats t = Odometer.trip(this);
        String msg = "Distancia: " + TripStats.formatKm(t.meters())
                + "\nTiempo en marcha: " + TripStats.formatDuration(t.movingMs()) + " h"
                + "\nVelocidad promedio: " + Math.round(t.avgKmh()) + " km/h"
                + "\nVelocidad máxima: " + Math.round(t.maxKmh()) + " km/h"
                + "\nManejando seguido: " + TripStats.formatDuration(t.sessionMs()) + " h";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Viaje actual")
                .setMessage(msg)
                .setPositiveButton("Reiniciar", (d, w) -> {
                    Odometer.resetTrip(this);
                    updateTrip();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void showCarMenu() {
        String[] items = {"Aceite y kilometraje", "Documentos del carro", "Viaje actual"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Mi carro")
                .setItems(items, (d, which) -> {
                    if (which == 0) startActivity(new Intent(this, OilActivity.class));
                    else if (which == 1) startActivity(new Intent(this, DocsActivity.class));
                    else showTripDialog();
                })
                .show();
    }

    private void onPhoneTap() {
        java.util.List<String[]> favs = new java.util.ArrayList<>();
        for (int i = 0; i < Profile.FAVORITES; i++) {
            String[] f = Profile.favorite(this, i);
            if (!f[1].isEmpty()) favs.add(f);
        }
        if (favs.isEmpty()) {
            openBluetooth();
            return;
        }
        String[] items = new String[favs.size() + 1];
        for (int i = 0; i < favs.size(); i++) {
            String[] f = favs.get(i);
            items[i] = "📞  " + (f[0].isEmpty() ? f[1] : f[0]);
        }
        items[favs.size()] = "Abrir teléfono";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Llamar a…")
                .setItems(items, (d, which) -> {
                    if (which == favs.size()) {
                        openBluetooth();
                        return;
                    }
                    String[] f = favs.get(which);
                    String who = f[0].isEmpty() ? f[1] : f[0];
                    if (nowPlaying.btClient.isBound() && nowPlaying.btClient.dial(f[1])) toast("Llamando a " + who + "…");
                    else toast("No se pudo llamar: ¿el celular está conectado?");
                })
                .show();
    }

    /** Name of the connected phone, read off the UI thread (it is a call into the factory Bluetooth service). */
    private void updatePhone() {
        if (!nowPlaying.btClient.isBound()) return;
        new Thread(() -> {
            String shown = phoneLabel();
            runOnUiThread(() -> {
                if (!isDestroyed()) phoneSub.setText(shown);
            });
        }, "phone-name").start();
    }

    private String phoneLabel() {
        try {
            if (nowPlaying.btClient.isPhoneConnected()) {
                String n = nowPlaying.btClient.deviceName();
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignored) {
            // fall through to the default text
        }
        return "Conectar dispositivos";
    }

    private void updateDocs() {
        int[] soon = DocsActivity.soonest(this);
        if (soon == null) {
            docsInfo.setVisibility(View.GONE);
            return;
        }
        docsInfo.setText(DocDates.describe(DocDates.NAMES[soon[0]], soon[1]));
        docsBg.setColor(soon[1] <= 7 ? 0xCCE53935 : 0xCCFF9800);
        docsInfo.setVisibility(View.VISIBLE);
    }

    // ---------- Sound profiles: Normal / Graves / Voz ----------

    private static final String[] SOUND_NAMES = {"Normal", "Graves", "Voz"};
    private static final int[] BASS_CURVE = {6, 6, 5, 4, 3, 3, 3, 3, 3, 3};

    private int soundProfile() {
        return getSharedPreferences("eq_profile", MODE_PRIVATE).getInt("profile", 0) % SOUND_NAMES.length;
    }

    private void updateSoundChip() {
        soundChip.setText("♪ " + (EqAuto.isEnabled(this) ? "Auto" : SOUND_NAMES[soundProfile()]));
    }

    private void cycleSoundProfile() {
        if (!sound.isConnected()) {
            toast("El ecualizador no está disponible");
            return;
        }
        int next = EqAuto.isEnabled(this) ? 0 : (soundProfile() + 1) % SOUND_NAMES.length;
        EqAuto.setEnabled(this, false); // a manual choice replaces the automatic curve
        getSharedPreferences("eq_profile", MODE_PRIVATE).edit().putInt("profile", next).apply();
        if (next == 0) {
            sound.setAudio(SoundClient.EQ_PRESET, 1); // standard, then the tone sliders back to the middle
            handler.postDelayed(() -> {
                sound.setAudio(SoundClient.BASS, 7);
                sound.setAudio(SoundClient.MIDDLE, 7);
                sound.setAudio(SoundClient.TREBLE, 7);
            }, 700);
        } else {
            sound.applyCurve(next == 1 ? BASS_CURVE : EqAuto.VOICE);
        }
        updateSoundChip();
    }

    /**
     * Idle card without notification access: open that setting (Spotify, YouTube Music… cannot be shown or
     * controlled without it). Otherwise open the player of the current source.
     */
    private void onMusicCardTap() {
        NowPlaying.Source s = nowPlaying.current();
        boolean idle = s == null || nowPlaying.track(s).title.isEmpty();
        if (idle && !nowPlaying.hasNotificationAccess()
                && safeStart(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) {
            return;
        }
        nowPlaying.open(this);
    }

    /** Until the first GPS fix, centre the map on the last known position (if it is recent). */
    private void seedMapPosition() {
        if (Odometer.fixAtMs != 0
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        android.location.Location best = null;
        for (String p : lm.getAllProviders()) {
            android.location.Location l;
            try {
                l = lm.getLastKnownLocation(p);
            } catch (Exception e) {
                continue;
            }
            if (l != null && System.currentTimeMillis() - l.getTime() < 24L * 3600 * 1000
                    && (best == null || l.getTime() > best.getTime())) {
                best = l;
            }
        }
        if (best != null) liveMap.setPosition(best.getLatitude(), best.getLongitude(), Float.NaN, -1f);
    }

    // ---------- Music (any source) ----------

    private View buildMusicCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0xFF1E4FD8, 0xFF3A86FF, 0xFF0B1E5C},
                        new int[]{0xFF12234A, 0xFF0B1630, 0xFF050A14}));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setClipToOutline(true);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x00000000, 0xCC050B1C}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        songSource = Ui.text(this, "MÚSICA", 12, Color.WHITE);
        songSource.setLetterSpacing(0.1f);
        songSource.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        songSource.setBackground(chip);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        slp.setMargins(dp(14), dp(14), 0, 0);
        card.addView(songSource, slp);

        soundChip = Ui.text(this, "", 12, Color.WHITE);
        soundChip.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable soundBg = new GradientDrawable();
        soundBg.setColor(0x40000000);
        soundBg.setCornerRadius(dp(14));
        soundChip.setBackground(soundBg);
        Ui.pressable(soundChip, 0.92f);
        soundChip.setOnClickListener(v -> cycleSoundProfile());
        FrameLayout.LayoutParams sclp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sclp.setMargins(0, dp(14), dp(14), 0);
        card.addView(soundChip, sclp);
        updateSoundChip();

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), 0, dp(16), dp(14));
        songTitle = Ui.text(this, "", 19, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(songTitle);
        songArtist = Ui.text(this, "", 14, 0xCCFFFFFF);
        songArtist.setPadding(0, dp(2), 0, dp(10));
        bottom.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 44, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 58, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(58), dp(58));
        plp.setMargins(dp(14), 0, dp(14), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 44, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        bottom.addView(controls);
        card.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> onMusicCardTap());
        return card;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(!empty ? t.artist
                : nowPlaying.hasNotificationAccess() ? "Toca para abrir la música"
                : "Toca para activar el control de apps");
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setAlpha(1f);
            art.setImageBitmap(t.art);
        } else {
            art.setPadding(dp(80), dp(44), dp(80), dp(124));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Color.WHITE);
            art.setAlpha(0.25f);
        }
    }

    // ---------- Left shortcut bar ----------

    private View buildSidebar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(dp(6), dp(8), dp(2), dp(10));
        bar.addView(sideIcon(R.drawable.ic_home, "Inicio", true, () -> { }), side());
        bar.addView(sideIcon(R.drawable.ic_compass, "Mapa", false, this::openMaps), side());
        bar.addView(sideIcon(R.drawable.ic_call, "Teléfono", false, this::openBluetooth), side());
        bar.addView(sideIcon(R.drawable.ic_music, "Música", false, () -> nowPlaying.open(this)), side());
        bar.addView(sideIcon(R.drawable.ic_apps, "Apps", false,
                () -> startActivity(new Intent(this, AppsActivity.class))), side());
        bar.addView(sideIcon(R.drawable.ic_settings, "Sonido", false, this::openEqualizer), side());
        bar.addView(sideIcon(R.drawable.ic_w_moon, "Pantalla", false,
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), side());
        bar.addView(sideIcon(R.drawable.ic_gear, "Ajustes", false, this::openSettings), side());
        return bar;
    }

    private LinearLayout.LayoutParams side() {
        return new LinearLayout.LayoutParams(-1, 0, 1f);
    }

    private View sideIcon(int icon, String label, boolean selected, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(selected ? Color.WHITE : 0xB3FFFFFF);
        item.addView(iv, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView tv = Ui.text(this, label, 11, selected ? Color.WHITE : 0xB3FFFFFF);
        tv.setPadding(0, dp(2), 0, 0);
        item.addView(tv, new LinearLayout.LayoutParams(-2, -2));
        if (selected) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x662F6BFF, 0x331F4FCC});
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), 0xFF4DA3FF);
            item.setBackground(bg);
        }
        f.addView(item, new FrameLayout.LayoutParams(dp(68), dp(58), Gravity.CENTER));
        Ui.pressable(item, 0.92f);
        item.setOnClickListener(v -> action.run());
        return f;
    }

    // ---------- Shortcuts: NWD screens, or the head unit's own apps elsewhere ----------

    private void openRadio() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, RadioActivity.class));
        else if (!launchByKeyword("radio", "fm", "tuner")) toast("No se encontró una app de radio");
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openEqualizer() {
        if (Profile.isNwd(this)) {
            startActivity(new Intent(this, EqActivity.class));
            return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(eq) && !launchByKeyword("ecualiz", "equaliz", "audio", "sonido", "dsp"))
            toast("No se encontró un ecualizador");
    }

    private void openSettings() {
        if (Profile.isNwd(this)) Ui.launch(this, SETTINGS);
        else safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private void openMaps() {
        Intent maps = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if (maps == null || !safeStart(maps)) {
            safeStart(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")));
        }
    }

    /** Opens the first installed app whose name or package matches one of the keywords. */
    private boolean launchByKeyword(String... keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(i);
                }
            }
        }
        return false;
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ---------- Lifecycle & helpers ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || !profileStamp().equals(createdProfile)) {
            // theme (setting, night or car lights) or profile (name, car photo) changed
            recreate();
            return;
        }
        updateGreeting();
        updateOil();
        updateDocs();
        updatePhone();
        updateTrip();
        updateSoundChip();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        boolean lights = hasLightsImage && carLightsOn();
        android.util.Log.d("CarLights", "onResume lights=" + lights);
        carLights.setAlpha(lights ? 1f : 0f);
        car.setAlpha(lights ? 0f : 1f);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        liveMap.setHint(hasLocationPermission() ? null : "Falta el permiso de ubicación · toca el mapa");
        seedMapPosition();
        handler.post(periodic);
        if (Profile.isNwd(this)) handler.post(radioPoll);
        handler.post(speedPoll);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(illReceiver);
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(radioPoll);
        handler.removeCallbacks(speedPoll);
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startService(new Intent(this, Odometer.class)); // start counting km now, not at the next boot
            liveMap.setHint(null);
            seedMapPosition();
            fetchWeather(true);
        } else if (code == 2) {
            // Asked from the map and refused (or the radio shows no dialog): open the app's settings page.
            safeStart(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
        sound.unbind(this);
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
