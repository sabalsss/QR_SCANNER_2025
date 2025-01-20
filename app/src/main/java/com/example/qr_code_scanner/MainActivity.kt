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
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private val cameraPermissionRequestCode = 101
    private var lastClickTime = 0L
    private val clickTime = 500L // ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        checkCameraPermission()

        if (savedInstanceState == null) {
            replaceFragment(CustomScannerFragment())
            binding.navigationView.setCheckedItem(R.id.nav_scanner)
        }

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
                R.id.nav_history -> HistoryFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> null
            }

            selectedFragment?.let {
                replaceFragment(it)
                drawerLayout.closeDrawers()
            }
            toggle.syncState() // Sync the drawer state after fragment change
            true
        }

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeFragment()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeFragment() {
        replaceFragment(CustomScannerFragment(), addToBackStack = false)
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()

        // Check if the fragment already exists in the manager
        val existingFragment = supportFragmentManager.findFragmentByTag(fragment::class.java.simpleName)

        if (existingFragment != null) {
            transaction.replace(R.id.fragment_container, existingFragment)
        } else {
            transaction.replace(R.id.fragment_container, fragment, fragment::class.java.simpleName)
        }

        transaction.setReorderingAllowed(true)
        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()

        updateToolbar(fragment)
        updateNavigationDrawerSelection(fragment)
        toggle.syncState()
    }
    private fun updateToolbar(fragment: Fragment) {
        setSupportActionBar(binding.toolbar)

        supportActionBar?.apply {
            // Set the title based on the fragment
            title = when (fragment) {
                is HistoryFragment -> getString(R.string.History)
                is SettingsFragment -> getString(R.string.settings_title)
                else -> getString(R.string.app_name) // Default title
            }

            // Set the icon based on the fragment
            val toolbarIcon = when (fragment) {
                is CustomScannerFragment -> R.drawable.qr_code_2_24px
                is HistoryFragment -> R.drawable.history_icon
                is SettingsFragment -> R.drawable.settings_icon
                else -> R.drawable.qr_code_2_24px // Default icon
            }

            setLogo(toolbarIcon) // Set the icon in the toolbar
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)

            // Adjust toolbar appearance
            if (fragment is CustomScannerFragment) {

                binding.toolbar.setBackgroundResource(R.drawable.transparent_toolbar_bg)
                window?.statusBarColor = ContextCompat.getColor(this@MainActivity, R.color.transparent_color) // Transparent status bar
            } else {

                binding.toolbar.setBackgroundResource(R.drawable.toolbar_bg)
            }
        }
    }
    private fun updateNavigationDrawerSelection(fragment: Fragment) {
        binding.navigationView.setCheckedItem(when (fragment) {
            is CustomScannerFragment -> R.id.nav_scanner
            is HistoryFragment -> R.id.nav_history
            is SettingsFragment -> R.id.nav_settings
            else -> R.id.nav_scanner
        })
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        when (currentFragment) {
            is CustomScannerFragment -> finish()
            is HistoryFragment -> replaceFragment(CustomScannerFragment(), addToBackStack = false)
            is SettingsFragment -> replaceFragment(HistoryFragment(), addToBackStack = false)
            else -> super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawerLayout.openDrawer(binding.navigationView)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
