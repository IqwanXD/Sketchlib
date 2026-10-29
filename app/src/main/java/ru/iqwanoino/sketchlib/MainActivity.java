package ru.iqwanoino.sketchlib;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Layar utama Sketchlib: search bar, daftar local library dengan mode pilih banyak,
 * FAB Download, dan drawer dorong. Keyboard dianimasikan mengikuti inset (API 30+).
 */
public class MainActivity extends Activity {

    static final String URL_DISCORD = "https://discord.gg/u7QPpQD7QC";
    static final String URL_GITHUB = "https://github.com/IqwanXD/";
    static final String URL_TELEGRAM = "https://t.me/IqwanoINO/";

    private PushDrawerLayout drawerLayout;
    private LinearLayout root, searchBar, normalBar, selectBar, drawerPanel;
    private FrameLayout topHolder, body;
    private EditText searchInput;
    private ImageView clearSearch;
    private TextView subtitle, selectTitle, emptyView;
    private ImageView selectAllBtn;
    private LibraryAdapter adapter;
    private ListView list;
    private SimpleSwipeRefreshLayout refresh;
    private LinearLayout fab;
    private int navPad;
    private String shownKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        Theme.load(this);
        shownKey = M3.paletteKey;
        Ui.setupEdge(this);
        // Android 11+: window tidak diubah ukurannya; panel dianimasikan manual (lihat installImeAnimation).
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        buildUi();
        reloadLibraries();
        // Saat dibuka: izin penyimpanan dulu, lalu izin notifikasi (bila belum diberikan).
        final Runnable askNotification = () -> AppSheets.notificationIfNeeded(this);
        if (!LibraryStore.hasStorageAccess(this)) {
            AppSheets.storage(this, askNotification);
        } else {
            askNotification.run();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Theme.load(this);
        if (!shownKey.equals(M3.paletteKey)) {
            rebuildInPlace();
            return;
        }
        if (adapter != null) reloadLibraries();
    }

    /**
     * Tema berubah di Pengaturan: bangun ulang tampilan di tempat, tanpa recreate()
     * (recreate memulai ulang activity dan terasa lambat). Teks pencarian dipertahankan.
     */
    private void rebuildInPlace() {
        String q = searchInput != null ? searchInput.getText().toString() : "";
        shownKey = M3.paletteKey;
        Ui.setupEdge(this);
        buildUi();
        if (!q.isEmpty()) searchInput.setText(q);
        reloadLibraries();
    }

    private void buildUi() {
        adapter = new LibraryAdapter(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);

        topHolder = buildTopBar();
        root.addView(topHolder, Ui.lp(-1, -2));
        root.addView(buildSearch(), Ui.lp(-1, Ui.dp(60), Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(12)));

        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(0x00000000));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setVerticalFadingEdgeEnabled(true);
        list.setFadingEdgeLength(Ui.dp(24));
        // Item terakhir tidak boleh tertutup FAB.
        list.setPadding(0, 0, 0, Ui.dp(96));
        list.setOnItemClickListener((AdapterView<?> parent, View v, int pos, long id) -> {
            if (adapter.isSelectionMode()) {
                adapter.toggle((Library) adapter.getItem(pos));
                onSelectionChanged();
            }
        });
        list.setOnItemLongClickListener((AdapterView<?> parent, View v, int pos, long id) -> {
            Library lib = (Library) adapter.getItem(pos);
            if (!adapter.isSelectionMode()) {
                adapter.setSelectionMode(true);
            }
            adapter.toggle(lib);
            onSelectionChanged();
            return true;
        });

        refresh = new SimpleSwipeRefreshLayout(this);
        refresh.addView(list, new FrameLayout.LayoutParams(-1, -1));
        refresh.setTargetView(list);
        refresh.setOnRefreshListener(() -> {
            reloadLibraries();
            refresh.setRefreshing(false);
        });

        emptyView = Ui.text(this, "", 14, Theme.ON_VAR, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);

        body = new FrameLayout(this);
        body.addView(refresh, new FrameLayout.LayoutParams(-1, -1));
        body.addView(emptyView, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        fab = buildFab();
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(-2, Ui.dp(56),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fabLp.bottomMargin = Ui.dp(20);
        body.addView(fab, fabLp);
        root.addView(body, Ui.lpWeight(-1, 0, 1f));

        drawerPanel = buildDrawer();
        ScrollView drawerScroll = new ScrollView(this);
        drawerScroll.setBackgroundColor(Theme.BG);
        drawerScroll.setVerticalScrollBarEnabled(false);
        drawerScroll.addView(drawerPanel, new FrameLayout.LayoutParams(-1, -2));

        drawerLayout = new PushDrawerLayout(this,
                (int) (getResources().getDisplayMetrics().widthPixels * 0.80f));
        drawerLayout.setViews(root, drawerScroll);
        drawerLayout.setOnApplyWindowInsetsListener((v, ins) -> {
            onInsets(ins);
            return ins;
        });
        setContentView(drawerLayout);
        Ui.applyFont(drawerLayout);
        installImeAnimation();
        applyInsets();
    }

    // ----- inset & keyboard -----

    /** Padding atas mengikuti status bar. Padding bawah (navigation bar) ada di root, lihat setNavPadding. */
    private void applyInsets() {
        if (topHolder == null || drawerPanel == null) return;
        topHolder.setPadding(Ui.dp(8), Ui.insetTop + Ui.dp(8), Ui.dp(8), Ui.dp(8));
        drawerPanel.setPadding(Ui.dp(24), Ui.insetTop + Ui.dp(24),
                Ui.dp(24), Ui.insetBottom + Ui.dp(24));
        updateBottomInset();
    }

    /** Tinggi navigation bar. Padding root hanya berubah saat navigation bar berubah. */
    private void setNavPadding(int px) {
        if (px == navPad) return;
        navPad = px;
        applyInsets();
    }

    /**
     * Daftar dan FAB memakai tinggi navigation bar. Root tidak di-padding, jadi list tampil
     * sampai bawah layar dan FAB melayang di atasnya (overlay), tidak menempel ke tombol navigasi.
     */
    private void updateBottomInset() {
        if (list != null) list.setPadding(0, 0, 0, Ui.dp(96) + Ui.insetBottom);
        if (fab != null && fab.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fab.getLayoutParams();
            int m = Ui.dp(20) + Ui.insetBottom;
            if (lp.bottomMargin != m) {
                lp.bottomMargin = m;
                fab.setLayoutParams(lp);
            }
        }
    }

    private static int navOf(WindowInsets ins) {
        if (Build.VERSION.SDK_INT >= 30) {
            return ins.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }
        return Ui.insetBottom;
    }

    private void onInsets(WindowInsets ins) {
        Ui.readInsets(ins);
        setNavPadding(navOf(ins));
        applyLift(ins);
        applyInsets();
    }

    /**
     * FAB naik sebesar tinggi keyboard di atasnya. Hanya translationY, jadi tidak ada
     * layout ulang setiap frame. Di bawah Android 11, adjustResize sudah menangani ini.
     */
    private void applyLift(WindowInsets ins) {
        if (Build.VERSION.SDK_INT < 30 || fab == null) return;
        int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
        float lift = Math.max(0, ime - navOf(ins));
        if (fab.getTranslationY() != -lift) fab.setTranslationY(-lift);
    }

    /** Android 11+: animasi keyboard diikuti frame demi frame lewat translationY. */
    private void installImeAnimation() {
        if (Build.VERSION.SDK_INT < 30) return;
        root.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(
                WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
            @Override
            public WindowInsets onProgress(WindowInsets insets,
                                           List<WindowInsetsAnimation> running) {
                applyLift(insets);
                return insets;
            }
        });
    }

    /** Sentuhan di luar search bar: lepas fokus dan tutup keyboard. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && searchInput != null && searchInput.hasFocus()) {
            Rect r = new Rect();
            searchBar.getGlobalVisibleRect(r);
            if (!r.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                searchInput.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ----- top bar (normal & mode pilih) -----

    private FrameLayout buildTopBar() {
        FrameLayout holder = new FrameLayout(this);
        holder.setBackgroundColor(Theme.BG);

        normalBar = new LinearLayout(this);
        normalBar.setOrientation(LinearLayout.HORIZONTAL);
        normalBar.setGravity(Gravity.CENTER_VERTICAL);
        int size = Ui.dp(44);
        normalBar.addView(Ui.iconButton(this, R.drawable.ic_menu, Theme.ON, 44,
                () -> drawerLayout.toggle()), Ui.lp(size, size));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(4), 0, Ui.dp(8), 0);
        titles.addView(Ui.text(this, "Sketchlib", Ui.TITLE_SP, Theme.ON, true), Ui.lp(-2, -2));
        subtitle = Ui.text(this, "", 12, Theme.ON_VAR, false);
        titles.addView(subtitle, Ui.lp(-2, -2));
        normalBar.addView(titles, Ui.lpWeight(0, -2, 1f));

        selectBar = new LinearLayout(this);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.setVisibility(View.GONE);
        selectBar.addView(Ui.iconButton(this, R.drawable.ic_clear, Theme.ON, 44,
                this::exitSelection), Ui.lp(size, size));
        selectTitle = Ui.text(this, "", Ui.TITLE_SP, Theme.ON, true);
        selectTitle.setPadding(Ui.dp(4), 0, Ui.dp(8), 0);
        selectBar.addView(selectTitle, Ui.lpWeight(0, -2, 1f));
        selectAllBtn = Ui.iconButton(this, R.drawable.ic_select_all, Theme.ON, 44,
                () -> {
                    adapter.toggleSelectAll();
                    onSelectionChanged();
                });
        selectBar.addView(selectAllBtn, Ui.lp(size, size));
        selectBar.addView(Ui.iconButton(this, R.drawable.ic_delete, Theme.ON, 44, this::askDelete),
                Ui.lp(size, size));

        holder.addView(normalBar, new FrameLayout.LayoutParams(-1, -2));
        holder.addView(selectBar, new FrameLayout.LayoutParams(-1, -2));
        return holder;
    }

    // ----- search bar -----

    private LinearLayout buildSearch() {
        searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(Ui.rect(Theme.SURFACE_HIGH, 28));
        searchBar.setPadding(Ui.dp(18), 0, Ui.dp(8), 0);

        searchBar.addView(Ui.icon(this, R.drawable.ic_search, Theme.ON_VAR), Ui.lp(Ui.dp(24), Ui.dp(24)));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Cari library");
        searchInput.setHintTextColor(Theme.ON_VAR);
        searchInput.setTextColor(Theme.ON);
        searchInput.setTextSize(17);
        searchInput.setTypeface(Fonts.regular(this));
        searchInput.setBackgroundColor(0x00000000);
        searchInput.setPadding(Ui.dp(14), Ui.dp(16), Ui.dp(4), Ui.dp(16));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s.toString());
                clearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                updateEmpty();
            }
        });
        searchBar.addView(searchInput, Ui.lpWeight(0, -2, 1f));

        clearSearch = Ui.iconButton(this, R.drawable.ic_clear, Theme.ON_VAR, 44,
                () -> searchInput.setText(""));
        clearSearch.setVisibility(View.GONE);
        searchBar.addView(clearSearch, Ui.lp(Ui.dp(44), Ui.dp(44)));
        return searchBar;
    }

    // ----- FAB -----

    /** Extended FAB: ikon dan teks Download, bentuk kapsul yang agak panjang. */
    private LinearLayout buildFab() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER);
        f.setPadding(Ui.dp(24), 0, Ui.dp(28), 0);
        f.setBackground(Ui.ripple(Theme.PRIMARY, 28));
        f.setElevation(Ui.dp(6));
        f.setClickable(true);

        f.addView(Ui.icon(this, R.drawable.ic_download, Theme.ON_PRIMARY), Ui.lp(Ui.dp(24), Ui.dp(24)));
        LinearLayout.LayoutParams tl = Ui.lp(-2, -2);
        tl.leftMargin = Ui.dp(12);
        f.addView(Ui.text(this, "Download", 16, Theme.ON_PRIMARY, true), tl);

        f.setOnClickListener(v -> DownloadSheet.show(this, this::reloadLibraries));
        return f;
    }

    // ----- mode pilih banyak -----

    private void onSelectionChanged() {
        // Tidak ada yang dipilih: keluar dari mode pilih banyak.
        if (adapter.isSelectionMode() && adapter.selectedCount() == 0) {
            adapter.setSelectionMode(false);
        }
        boolean on = adapter.isSelectionMode();
        normalBar.setVisibility(on ? View.GONE : View.VISIBLE);
        selectBar.setVisibility(on ? View.VISIBLE : View.GONE);
        fab.setVisibility(on ? View.GONE : View.VISIBLE);
        selectTitle.setText(adapter.selectedCount() + " dipilih");
        selectAllBtn.setAlpha(adapter.selectedCount() > 0 ? 1f : 0.7f);
    }

    private void exitSelection() {
        adapter.setSelectionMode(false);
        onSelectionChanged();
    }

    private void askDelete() {
        List<Library> picked = adapter.selectedItems();
        if (picked.isEmpty()) {
            M3.toast(this, "Pilih library dulu");
            return;
        }
        AppSheets.confirmDelete(this, picked, () -> {
            exitSelection();
            reloadLibraries();
        });
    }

    // ----- drawer -----

    /** Drawer: tata letak sama dengan drawer Weblab. */
    private LinearLayout buildDrawer() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(Theme.BG);
        d.setClickable(true);

        d.addView(Ui.text(this, "Sketchlib", 34, Theme.PRIMARY, true), Ui.lp(-2, -2));
        d.addView(Ui.text(this, "Kelola local library Sketchware dengan mudah", 14, Theme.ON_VAR, false),
                Ui.lp(-2, -2, 0, 0, 0, Ui.dp(20)));

        d.addView(drawerItem(R.drawable.ic_info, "Informasi", () -> AppSheets.info(this)));
        d.addView(drawerItem(R.drawable.ic_settings, "Pengaturan",
                () -> startActivity(new Intent(this, SettingsActivity.class))));
        d.addView(divider(), Ui.lp(-1, Ui.dp(1), 0, Ui.dp(12), 0, Ui.dp(12)));

        TextView related = Ui.text(this, "Tautan terkait", 14, Theme.ON_VAR, true);
        related.setPadding(0, 0, 0, Ui.dp(8));
        d.addView(related, Ui.lp(-2, -2));
        d.addView(drawerItem(R.drawable.ic_discord, "Discord", () -> openUrl(URL_DISCORD)));
        d.addView(drawerItem(R.drawable.ic_github, "GitHub", () -> openUrl(URL_GITHUB)));
        d.addView(drawerItem(R.drawable.ic_telegram, "Telegram", () -> openUrl(URL_TELEGRAM)));
        return d;
    }

    private LinearLayout drawerItem(int icon, String label, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
        row.setBackground(Ui.ripple(0, 12));
        row.setClickable(true);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.tinted(this, icon, Theme.ON_VAR));
        row.addView(iv, Ui.lp(Ui.dp(26), Ui.dp(26)));

        LinearLayout.LayoutParams tl = Ui.lp(-2, -2);
        tl.leftMargin = Ui.dp(20);
        row.addView(Ui.text(this, label, 16, Theme.ON, false), tl);

        row.setOnClickListener(v -> {
            drawerLayout.close();
            action.run();
        });
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(M3.alpha(Theme.ON, 0.2f));
        return v;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            M3.toast(this, "Tidak bisa membuka tautan");
        }
    }

    // ----- data -----

    private void reloadLibraries() {
        adapter.setItems(LibraryStore.loadAll());
        if (subtitle != null) subtitle.setText(adapter.allCount() + " local library");
        updateEmpty();
        if (adapter.isSelectionMode()) onSelectionChanged();
    }

    private void updateEmpty() {
        if (emptyView == null) return;
        boolean noneAtAll = adapter.allCount() == 0;
        emptyView.setText(noneAtAll
                ? "Belum ada local library.\nTekan Download untuk menambah."
                : "Tidak ada library yang cocok.");
        emptyView.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isOpen()) {
            drawerLayout.close();
        } else if (Sheet.current != null) {
            Sheet.current.dismiss();
        } else if (adapter != null && adapter.isSelectionMode()) {
            exitSelection();
        } else {
            super.onBackPressed();
        }
    }
}
