package com.example.limbo.Views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.example.limbo.R

class SplashActivity : AppCompatActivity() {

    private lateinit var logoImageView: ImageView
    private lateinit var sloganTextView: TextView
    private lateinit var appNameTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ocultar action bar y hacer fullscreen
        supportActionBar?.hide()
        window.statusBarColor = getColor(R.color.black)

        setContentView(R.layout.activity_splash)

        // Deshabilitar el botón de atrás usando el nuevo sistema
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // No hacer nada - deshabilita el botón de atrás
            }
        })

        initViews()
        startAnimations()
    }

    private fun initViews() {
        logoImageView = findViewById(R.id.logoImageView)
        sloganTextView = findViewById(R.id.sloganTextView)
        appNameTextView = findViewById(R.id.appNameTextView)

        // Inicializar vistas invisibles para la animación
        logoImageView.alpha = 0f
        logoImageView.scaleX = 0f
        logoImageView.scaleY = 0f

        appNameTextView.alpha = 0f
        appNameTextView.translationY = 100f

        sloganTextView.alpha = 0f
        sloganTextView.translationY = 50f
    }

    private fun startAnimations() {
        // Animación del logo (aparece con escala y rotación)
        val logoFadeIn = ObjectAnimator.ofFloat(logoImageView, "alpha", 0f, 1f)
        val logoScaleX = ObjectAnimator.ofFloat(logoImageView, "scaleX", 0f, 1.2f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(logoImageView, "scaleY", 0f, 1.2f, 1f)
        val logoRotation = ObjectAnimator.ofFloat(logoImageView, "rotation", -10f, 0f)

        val logoAnimatorSet = AnimatorSet().apply {
            playTogether(logoFadeIn, logoScaleX, logoScaleY, logoRotation)
            duration = 1200
            interpolator = OvershootInterpolator(1.2f)
        }

        // Animación del nombre de la app
        val appNameFadeIn = ObjectAnimator.ofFloat(appNameTextView, "alpha", 0f, 1f)
        val appNameSlideUp = ObjectAnimator.ofFloat(appNameTextView, "translationY", 100f, 0f)

        val appNameAnimatorSet = AnimatorSet().apply {
            playTogether(appNameFadeIn, appNameSlideUp)
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 400
        }

        // Animación del slogan
        val sloganFadeIn = ObjectAnimator.ofFloat(sloganTextView, "alpha", 0f, 1f)
        val sloganSlideUp = ObjectAnimator.ofFloat(sloganTextView, "translationY", 50f, 0f)

        val sloganAnimatorSet = AnimatorSet().apply {
            playTogether(sloganFadeIn, sloganSlideUp)
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 800
        }

        // Efecto de pulsación en el logo
        val pulsateAnimator = ObjectAnimator.ofFloat(logoImageView, "scaleX", 1f, 1.05f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            startDelay = 1500
        }

        val pulsateAnimatorY = ObjectAnimator.ofFloat(logoImageView, "scaleY", 1f, 1.05f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            startDelay = 1500
        }

        // Secuencia principal de animaciones
        val mainSequence = AnimatorSet().apply {
            playSequentially(logoAnimatorSet, appNameAnimatorSet, sloganAnimatorSet)
            doOnEnd {
                // Iniciar efecto de pulsación
                pulsateAnimator.start()
                pulsateAnimatorY.start()

                // Esperar un poco más y luego ir a MainActivity
                Handler(Looper.getMainLooper()).postDelayed({
                    navigateToMainActivity()
                }, 1500)
            }
        }

        mainSequence.start()
    }

    private fun navigateToMainActivity() {
        // Animación de salida
        val fadeOut = ObjectAnimator.ofFloat(findViewById<View>(R.id.splashContainer), "alpha", 1f, 0f)
        fadeOut.duration = 500
        fadeOut.doOnEnd {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()

            // Transición personalizada
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        fadeOut.start()
    }

    // Método deprecated eliminado - usar onBackPressedDispatcher en su lugar
}