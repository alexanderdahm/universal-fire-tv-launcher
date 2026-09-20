import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ═══════════════════════════════════════════════════════════════════════════
//  LAUNCHER CONFIGURATION
//
//  This is the only block that has to be touched to turn this project into a
//  launcher for a different application. Every value can also be overridden
//  from the command line without editing the file, which is what the CI build
//  matrix does:
//
//      ./gradlew assembleRelease \
//          -PtargetPackage=org.xbmc.kodi \
//          -PappLabel="Kodi Launcher" \
//          -PappIconColor="#FF00A8E1" \
//          -PappIdSuffix=".kodi" \
//          -Pautostart=true
// ═══════════════════════════════════════════════════════════════════════════

/** Package of the app that should be started. Empty = show the app picker. */
val targetPackage: String = launcherProperty("targetPackage", "")

/** Name of this launcher on the Fire TV home screen, and in the error dialog. */
val appLabel: String = launcherProperty("appLabel", "Universal Launcher")

/** Accent colour of the generated launcher icon and TV banner (#AARRGGBB). */
val appIconColor: String = launcherProperty("appIconColor", "#FF2F7DF6")

/** Appended to the application id so several launchers can coexist. */
val appIdSuffix: String = launcherProperty("appIdSuffix", "")

/**
 * Single app builds have no picker to configure autostart from, so they carry
 * the setting in the build: `-Pautostart=true` makes such a build start its
 * target app once the Fire TV has booted. Picker builds ignore it - there the
 * user picks the autostart app on the device.
 */
val autostart: Boolean = launcherProperty("autostart", "false").toBoolean()

/** Application id all builds are derived from. */
val baseApplicationId = "com.example.universallauncher"

/** Reads `-Pname=value`, falling back to the default defined above. */
fun launcherProperty(name: String, defaultValue: String): String =
    providers.gradleProperty(name).getOrElse(defaultValue)

// ═══════════════════════════════════════════════════════════════════════════

android {
    namespace = baseApplicationId

    // Compiled against API 34, but the app behaves like a targetSdk 30 app,
    // which keeps it well behaved on Fire OS 5 (API 22) devices.
    compileSdk = 34

    defaultConfig {
        applicationId = baseApplicationId + appIdSuffix
        minSdk = 21
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        // The single source of truth for the app, mirrored into BuildConfig.
        buildConfigField("String", "TARGET_PACKAGE", "\"$targetPackage\"")
        buildConfigField("String", "APP_LABEL", "\"$appLabel\"")
        buildConfigField("String", "APP_ICON_COLOR", "\"$appIconColor\"")
        buildConfigField("boolean", "AUTOSTART_TARGET", "$autostart")

        // The same values as resources: the launcher tile label and the colour
        // used by the adaptive icon (API 26+) and by the picker UI.
        resValue("string", "app_name", appLabel)
        resValue("color", "launcher_icon_color", appIconColor)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign the release build with the auto generated debug key so that CI
            // can produce an installable APK without any signing secrets.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // TARGET_PACKAGE / APP_LABEL / APP_ICON_COLOR live here.
        buildConfig = true
    }

    lint {
        // targetSdk 30 is required for Fire OS 5 and trips ExpiredTargetSdkVersion.
        abortOnError = false
    }

    testOptions {
        unitTests {
            // Local JVM tests run against the stubbed android.jar; returning
            // defaults instead of throwing keeps them free of a mocking
            // framework.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // AlertDialog that works down to API 21.
    implementation("androidx.appcompat:appcompat:1.6.1")

    // D-pad friendly grid used by the app picker on TV devices.
    implementation("androidx.leanback:leanback:1.0.0")

    // Local unit tests; no mocking framework, the fakes are written by hand.
    testImplementation("junit:junit:4.13.2")
}

// ═══════════════════════════════════════════════════════════════════════════
//  Artwork generation
//
//  The launcher icon and the TV banner are drawn at build time from the
//  configuration above, so a rebranded build needs no new image files: the
//  accent colour and the label are baked into freshly rendered PNGs.
// ═══════════════════════════════════════════════════════════════════════════

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val artwork = tasks.register<GenerateLauncherArtworkTask>("generate${variantName}LauncherArtwork") {
            accentColor.set(appIconColor)
            label.set(appLabel)
        }
        // AGP wires the generated folder into this variant's resource merge.
        variant.sources.res?.addGeneratedSourceDirectory(
            artwork,
            GenerateLauncherArtworkTask::outputDirectory
        )
    }
}

/**
 * Renders the launcher icon (rounded square), the round launcher icon (circle)
 * and the 16:9 Fire TV banner for every density bucket.
 *
 * Pure Java2D, so it works headless on any JDK 17 - no image tooling, no
 * checked in artwork, and nothing copyrighted.
 */
abstract class GenerateLauncherArtworkTask : DefaultTask() {

    /** Accent colour as `#RRGGBB` or `#AARRGGBB`. */
    @get:Input
    abstract val accentColor: Property<String>

    /** Text drawn onto the TV banner. */
    @get:Input
    abstract val label: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        System.setProperty("java.awt.headless", "true")

        val accent = parseColor(accentColor.get())
        val root = outputDirectory.get().asFile
        root.deleteRecursively()

        // Launcher icons: 48dp at every density bucket.
        ICON_SIZES.forEach { (qualifier, size) ->
            val dir = File(root, "mipmap-$qualifier").apply { mkdirs() }
            ImageIO.write(renderIcon(size, accent, circular = false), "png", File(dir, "ic_launcher.png"))
            ImageIO.write(renderIcon(size, accent, circular = true), "png", File(dir, "ic_launcher_round.png"))
        }

        // TV banner: 320x180 at xhdpi, as required by Fire TV.
        BANNER_WIDTHS.forEach { (qualifier, width) ->
            val dir = File(root, "drawable-$qualifier").apply { mkdirs() }
            val banner = renderBanner(width, width * 9 / 16, accent, label.get())
            ImageIO.write(banner, "png", File(dir, "banner.png"))
        }

        logger.lifecycle("Generated launcher artwork in ${root.absolutePath} (accent ${accentColor.get()})")
    }

    /** Rounded square or circular plate with a white play triangle on top. */
    private fun renderIcon(size: Int, accent: Color, circular: Boolean): BufferedImage {
        val image = newImage(size, size)
        val g = image.newGraphics()

        g.paint = GradientPaint(0f, 0f, accent, size.toFloat(), size.toFloat(), accent.darkenBy(0.22f))
        if (circular) {
            g.fillOval(0, 0, size, size)
        } else {
            val radius = size * 0.22f
            g.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), radius * 2, radius * 2))
        }

        g.color = Color.WHITE
        g.fill(playTriangle(size * 0.52f, size / 2f, size * 0.42f))

        g.dispose()
        return image
    }

    /** Dark 16:9 banner: icon mark on the left, app label next to it. */
    private fun renderBanner(width: Int, height: Int, accent: Color, text: String): BufferedImage {
        val image = newImage(width, height)
        val g = image.newGraphics()

        val backdrop = accent.darkenBy(0.90f)
        g.paint = GradientPaint(0f, 0f, BANNER_BACKGROUND, width.toFloat(), height.toFloat(), backdrop)
        g.fillRect(0, 0, width, height)

        val mark = height * 0.46f
        val markX = width * 0.09f
        val markY = (height - mark) / 2f
        g.paint = GradientPaint(markX, markY, accent, markX + mark, markY + mark, accent.darkenBy(0.22f))
        g.fill(RoundRectangle2D.Float(markX, markY, mark, mark, mark * 0.44f, mark * 0.44f))

        g.color = Color.WHITE
        g.fill(playTriangle(markX + mark * 0.54f, height / 2f, mark * 0.42f))

        drawBannerLabel(g, text, left = markX + mark * 1.30f, width = width, height = height)

        g.dispose()
        return image
    }

    /**
     * Draws the label, shrinking it until it fits. Rendering text needs fonts on
     * the build machine, so a headless box without any is tolerated: the banner
     * simply keeps the graphical mark.
     */
    private fun drawBannerLabel(
        g: java.awt.Graphics2D,
        text: String,
        left: Float,
        width: Int,
        height: Int
    ) {
        runCatching {
            val available = width - left - width * 0.06f
            var fontSize = height * 0.17f
            var metrics = g.getFontMetrics(Font(Font.SANS_SERIF, Font.BOLD, fontSize.toInt()))
            while (fontSize > 6f && metrics.stringWidth(text) > available) {
                fontSize -= 1f
                metrics = g.getFontMetrics(Font(Font.SANS_SERIF, Font.BOLD, fontSize.toInt()))
            }
            g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize.toInt())
            g.color = Color.WHITE
            val baseline = height / 2f + metrics.ascent / 2f - metrics.descent / 4f
            g.drawString(text, left, baseline)
        }.onFailure {
            logger.warn("Banner label could not be rendered (${it.message}); using the mark only")
        }
    }

    /** Play triangle pointing right, centred on ([centerX], [centerY]). */
    private fun playTriangle(centerX: Float, centerY: Float, size: Float): Path2D.Float {
        val halfHeight = size / 2f
        val width = size * 0.86f
        return Path2D.Float().apply {
            moveTo((centerX - width / 3f).toDouble(), (centerY - halfHeight).toDouble())
            lineTo((centerX - width / 3f).toDouble(), (centerY + halfHeight).toDouble())
            lineTo((centerX + width * 2f / 3f).toDouble(), centerY.toDouble())
            closePath()
        }
    }

    private fun newImage(width: Int, height: Int) =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    private fun BufferedImage.newGraphics(): java.awt.Graphics2D = createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    private fun Color.darkenBy(factor: Float): Color = Color(
        (red * (1f - factor)).toInt().coerceIn(0, 255),
        (green * (1f - factor)).toInt().coerceIn(0, 255),
        (blue * (1f - factor)).toInt().coerceIn(0, 255),
        alpha
    )

    /** Accepts `#RRGGBB` and `#AARRGGBB`. */
    private fun parseColor(value: String): Color {
        val hex = value.removePrefix("#")
        require(hex.length == 6 || hex.length == 8) {
            "appIconColor must be #RRGGBB or #AARRGGBB but was '$value'"
        }
        val argb = hex.toLong(16)
        return if (hex.length == 8) {
            Color(
                ((argb shr 16) and 0xFF).toInt(),
                ((argb shr 8) and 0xFF).toInt(),
                (argb and 0xFF).toInt(),
                ((argb shr 24) and 0xFF).toInt()
            )
        } else {
            Color(
                ((argb shr 16) and 0xFF).toInt(),
                ((argb shr 8) and 0xFF).toInt(),
                (argb and 0xFF).toInt()
            )
        }
    }

    private companion object {
        val BANNER_BACKGROUND: Color = Color(14, 17, 22)

        /** Launcher icon edge length per density bucket (48dp). */
        val ICON_SIZES = linkedMapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        )

        /** Banner width per density bucket (160dp x 90dp). */
        val BANNER_WIDTHS = linkedMapOf(
            "mdpi" to 160,
            "hdpi" to 240,
            "xhdpi" to 320,
            "xxhdpi" to 480,
            "xxxhdpi" to 640
        )
    }
}
