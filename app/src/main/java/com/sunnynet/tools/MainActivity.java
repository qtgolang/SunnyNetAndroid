package com.sunnynet.tools;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.sunnynet.tools.capture.CaptureEngine;
import com.sunnynet.tools.ui.CaptureFragment;
import com.sunnynet.tools.ui.CaptureRangeFragment;
import com.sunnynet.tools.ui.CaptureTargetFragment;
import com.sunnynet.tools.ui.CertFragment;
import com.sunnynet.tools.ui.KeyboardDismissHelper;
import com.sunnynet.tools.ui.OpenSourceLicensesFragment;
import com.sunnynet.tools.ui.RuleListFragment;
import com.sunnynet.tools.ui.RulesSettingsFragment;
import com.sunnynet.tools.ui.SessionFragment;
import com.sunnynet.tools.ui.SettingsFragment;
import com.sunnynet.tools.capture.CaptureRule;

/**
 * 应用壳：侧滑抽屉切换工作台 / 抓包目标 / 抓取范围 / 规则设置 / 端口设置 / 证书 / 会话文件 / 开源协议。
 */
public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.nav_view);
        View appBar = findViewById(R.id.app_bar);
        View fragmentContainer = findViewById(R.id.fragment_container);
        setSupportActionBar(toolbar);

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        TextView headerVersion = navigationView.getHeaderView(0).findViewById(R.id.nav_header_version);
        headerVersion.setText(getString(R.string.sdk_version_label, CaptureEngine.versionText()));

        navigationView.setNavigationItemSelectedListener(this::onNavItemSelected);

        // 状态栏 inset 作用于顶栏容器（增高），勿直接 padding Toolbar，否则会裁切标题
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, insets) -> {
            Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), status.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(nav.left, 0, nav.right, nav.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            navigationView.setCheckedItem(R.id.nav_workbench);
            showFragment(new CaptureFragment(), R.string.nav_workbench);
        }
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_workbench) {
            showFragment(new CaptureFragment(), R.string.nav_workbench);
        } else if (id == R.id.nav_capture_target) {
            showFragment(new CaptureTargetFragment(), R.string.nav_capture_target);
        } else if (id == R.id.nav_capture_range) {
            showFragment(new CaptureRangeFragment(), R.string.nav_capture_range);
        } else if (id == R.id.nav_rules) {
            showFragment(new RulesSettingsFragment(), R.string.nav_rules);
        } else if (id == R.id.nav_settings) {
            showFragment(new SettingsFragment(), R.string.nav_settings);
        } else if (id == R.id.nav_cert) {
            showFragment(new CertFragment(), R.string.nav_cert);
        } else if (id == R.id.nav_session) {
            showFragment(new SessionFragment(), R.string.nav_session);
        } else if (id == R.id.nav_licenses) {
            showFragment(new OpenSourceLicensesFragment(), R.string.nav_licenses);
        } else {
            return false;
        }
        drawerLayout.closeDrawers();
        return true;
    }

    /** 打开某一类规则列表（压入返回栈）。 */
    public void navigateToRuleList(@NonNull CaptureRule.Type type) {
        RuleListFragment fragment = RuleListFragment.newInstance(type);
        View statusPanel = findViewById(R.id.toolbar_capture_status);
        if (statusPanel != null) {
            statusPanel.setVisibility(View.GONE);
        }
        toolbar.setTitle(RuleListFragment.titleResForType(type));
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack("rule_list")
                .commit();
    }

    /** 从工作台等页面跳转到端口设置（同步侧栏选中与标题）。 */
    public void navigateToSettings() {
        navigationView.setCheckedItem(R.id.nav_settings);
        showFragment(new SettingsFragment(), R.string.nav_settings);
    }

    private void showFragment(Fragment fragment, int titleRes) {
        View statusPanel = findViewById(R.id.toolbar_capture_status);
        if (statusPanel != null) {
            statusPanel.setVisibility(fragment instanceof CaptureFragment ? View.VISIBLE : View.GONE);
        }
        toolbar.setTitle(titleRes);
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        KeyboardDismissHelper.consumeOutsideTapHideIme(getWindow(), ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawers();
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            getSupportFragmentManager().executePendingTransactions();
            restoreToolbarTitleForCurrentFragment();
            return;
        }
        super.onBackPressed();
    }

    private void restoreToolbarTitleForCurrentFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        View statusPanel = findViewById(R.id.toolbar_capture_status);
        if (fragment instanceof CaptureFragment) {
            if (statusPanel != null) {
                statusPanel.setVisibility(View.VISIBLE);
            }
            toolbar.setTitle(R.string.nav_workbench);
        } else if (fragment instanceof CaptureTargetFragment) {
            toolbar.setTitle(R.string.nav_capture_target);
        } else if (fragment instanceof CaptureRangeFragment) {
            toolbar.setTitle(R.string.nav_capture_range);
        } else if (fragment instanceof RulesSettingsFragment) {
            toolbar.setTitle(R.string.nav_rules);
        } else if (fragment instanceof SettingsFragment) {
            toolbar.setTitle(R.string.nav_settings);
        } else if (fragment instanceof CertFragment) {
            toolbar.setTitle(R.string.nav_cert);
        } else if (fragment instanceof SessionFragment) {
            toolbar.setTitle(R.string.nav_session);
        } else if (fragment instanceof OpenSourceLicensesFragment) {
            toolbar.setTitle(R.string.nav_licenses);
        } else if (fragment instanceof RuleListFragment) {
            toolbar.setTitle(((RuleListFragment) fragment).getTitleRes());
        }
        if (!(fragment instanceof CaptureFragment) && statusPanel != null) {
            statusPanel.setVisibility(View.GONE);
        }
    }
}
