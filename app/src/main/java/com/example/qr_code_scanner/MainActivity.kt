package com.example.qr_code_scanner

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.qr_code_scanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private val cameraPermissionRequestCode = 101
    private var lastClickTime = 0L
    private val clickTime = 500L // ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        checkCameraPermission()

        if (savedInstanceState == null) {
            initializeDefaultFragment()
        }

        setupNavigationListener()
    }


//    private fun showFirstLaunchDialog() {
//        val builder = AlertDialog.Builder(this)
//        val dialogView = layoutInflater.inflate(R.layout.dialog_first_launch, null)
//        builder.setView(dialogView)
//            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
//            .create()
//            .show()
//    }

    private fun setupDrawer() {
        drawerLayout = binding.drawerLayout
        setSupportActionBar(binding.toolbar)

        // Initialize the ActionBarDrawerToggle to handle the drawer icon
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )

        // Sync the toggle state
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Customize the menu icon color (optional)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu) // Customize the icon if needed
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        } else {
            initializeFragment()
        }
    }

    private fun initializeDefaultFragment() {
        replaceFragment(
            CustomScannerFragment(),
            addToBackStack = false
        )
        binding.navigationView.setCheckedItem(R.id.nav_scanner)
    }
    private fun setupNavigationListener() {
        binding.navigationView.setNavigationItemSelectedListener { item ->
            if (System.currentTimeMillis() - lastClickTime < clickTime) return@setNavigationItemSelectedListener false
            lastClickTime = System.currentTimeMillis()

            val selectedFragment = when (item.itemId) {
                R.id.nav_scanner -> {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is CustomScannerFragment) {
                        drawerLayout.closeDrawers()
                        return@setNavigationItemSelectedListener false
                    }
                    CustomScannerFragment()
                }

                R.id.nav_history -> {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is HistoryFragment) {
                        drawerLayout.closeDrawers()
                        return@setNavigationItemSelectedListener false
                    }
                    HistoryFragment()
                }

                R.id.nav_settings -> {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is SettingsFragment) {
                        drawerLayout.closeDrawers()
                        return@setNavigationItemSelectedListener false
                    }
                    SettingsFragment()
                }

                else -> null
            }

            selectedFragment?.let { replaceFragment(it) }
            toggle.syncState() // Sync the drawer state after fragment change
            true
        }
    }

    private fun initializeFragment() {
        replaceFragment(CustomScannerFragment(), addToBackStack = false)
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment != null && currentFragment.javaClass == fragment.javaClass) {
            drawerLayout.closeDrawers()
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment, fragment::class.java.simpleName)
        transaction.setReorderingAllowed(true)

        // Only add to back stack if it's not a direct navigation to ScannerFragment
        if (addToBackStack && !(fragment is CustomScannerFragment && (currentFragment is HistoryFragment || currentFragment is SettingsFragment))) {
            transaction.addToBackStack(null)
        }

        transaction.commit()

        // Close the drawer immediately after the fragment is replaced
        drawerLayout.closeDrawers()

        updateToolbar(fragment)
        updateNavigationDrawerSelection(fragment)
        toggle.syncState()
    }


    private fun updateToolbar(fragment: Fragment) {
        setSupportActionBar(binding.toolbar)

        supportActionBar?.apply {
            title = when (fragment) {
                is HistoryFragment -> getString(R.string.History)
                is SettingsFragment -> getString(R.string.settings_title)
                else -> getString(R.string.scanner_fragment) // Default title
            }

            // Customizing the home icon based on the fragment
            when (fragment) {
                is CustomScannerFragment -> supportActionBar?.setIcon(R.drawable.scanner_frame_toolbar)
                is SettingsFragment -> supportActionBar?.setIcon(R.drawable.settings_icon)
                else -> supportActionBar?.setIcon(R.drawable.history_icon)
            }
        }
    }

    private fun updateNavigationDrawerSelection(fragment: Fragment) {
        binding.navigationView.setCheckedItem(
            when (fragment) {
                is CustomScannerFragment -> R.id.nav_scanner
                is HistoryFragment -> R.id.nav_history
                is SettingsFragment -> R.id.nav_settings
                else -> R.id.nav_scanner
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == cameraPermissionRequestCode &&
            permissions.contains(Manifest.permission.CAMERA) &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initializeFragment()
        } else if (requestCode == cameraPermissionRequestCode) {
            Toast.makeText(
                this,
                "Camera permission is required to scan QR codes",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (currentFragment) {
            is HistoryFragment -> {
                if (currentFragment.isMultiSelectMode) {
                    currentFragment.exitMultiSelectMode() // Exit selection mode first
                    return
                }
                replaceFragment(CustomScannerFragment(), addToBackStack = false)
            }
            is SettingsFragment -> {
                replaceFragment(CustomScannerFragment(), addToBackStack = false)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawerLayout.openDrawer(binding.navigationView)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
