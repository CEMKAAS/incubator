import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;

/**
 * Генератор app/src/main/res/drawable-nodpi/img_candling_1..3.jpg. Запуск без Gradle:
 *   "<JBR>/bin/java" tools/CandlingImages.java app/src/main/res/drawable-nodpi
 * Всё детерминировано (seed на стадию), так что повторный прогон даёт те же снимки.
 *
 * Renders the three candling reference pictures as "photos": an egg lit from below in a
 * dark room. Colour comes from Beer–Lambert absorption per channel — shell, tissue and
 * blood each absorb blue far more than red — so everything denser reads darker and redder,
 * exactly as it does through a real candler.
 */
public class CandlingImages {
    static final int W = 1000, H = 1000;
    static final double CX = 500, A = 262, HT = 340, HB = 432, YW = 112 + HT;
    static final double TOP = YW - HT, BOTTOM = YW + HB;
    static final double[] BG = {14 / 255.0, 9 / 255.0, 7 / 255.0};

    static long seed;

    // ---------- noise ----------
    static double hash(int x, int y, int s) {
        long h = x * 374761393L + y * 668265263L + s * 1442695040888963407L + seed;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    static double smooth(double t) { return t * t * (3 - 2 * t); }

    static double vnoise(double x, double y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = smooth(x - xi), fy = smooth(y - yi);
        double a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    static double fbm(double x, double y, int oct, int s) {
        double sum = 0, amp = 0.5, f = 1, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += amp * vnoise(x * f, y * f, s + i * 17);
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }

    // ---------- egg geometry ----------
    static double eggR(double x, double y) {
        double h = y < YW ? HT : HB;
        double dx = (x - CX) / A, dy = (y - YW) / h;
        return Math.sqrt(dx * dx + dy * dy);
    }

    static double halfWidthAt(double y) {
        double h = y < YW ? HT : HB;
        double dy = (y - YW) / h;
        return dy * dy >= 1 ? 0 : A * Math.sqrt(1 - dy * dy);
    }

    // ---------- blur ----------
    static void boxBlur(float[] src, int r) {
        if (r < 1) return;
        float[] tmp = new float[src.length];
        for (int y = 0; y < H; y++) {
            double acc = 0;
            int row = y * W;
            for (int x = -r; x <= r; x++) acc += src[row + Math.max(0, Math.min(W - 1, x))];
            for (int x = 0; x < W; x++) {
                tmp[row + x] = (float) (acc / (2 * r + 1));
                acc += src[row + Math.min(W - 1, x + r + 1)] - src[row + Math.max(0, x - r)];
            }
        }
        for (int x = 0; x < W; x++) {
            double acc = 0;
            for (int y = -r; y <= r; y++) acc += tmp[Math.max(0, Math.min(H - 1, y)) * W + x];
            for (int y = 0; y < H; y++) {
                src[y * W + x] = (float) (acc / (2 * r + 1));
                acc += tmp[Math.min(H - 1, y + r + 1) * W + x] - tmp[Math.max(0, y - r) * W + x];
            }
        }
    }

    static void gauss(float[] f, int r) { for (int i = 0; i < 3; i++) boxBlur(f, r); }

    // ---------- vessels ----------
    interface Air { double y(double x); }

    static BufferedImage vesselCanvas;
    static Graphics2D vg;
    static Random rnd;

    static void branch(double x, double y, double ang, double width, double len, int depth, Air air, double ox, double oy) {
        double step = 3.0;
        double walked = 0;
        double nextSplit = len * (0.22 + rnd.nextDouble() * 0.2);
        int splits = 0;
        while (walked < len) {
            double nx = x + Math.cos(ang) * step, ny = y + Math.sin(ang) * step;
            if (eggR(nx, ny) > 0.965 || ny < air.y(nx) + 2) return;
            vg.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            vg.draw(new Line2D.Double(x, y, nx, ny));
            x = nx; y = ny; walked += step;
            ang += rnd.nextGaussian() * 0.11;
            // drift away from the origin so the net spreads instead of curling back
            double away = Math.atan2(y - oy, x - ox);
            double diff = Math.atan2(Math.sin(away - ang), Math.cos(away - ang));
            ang += diff * 0.02;
            width = Math.max(0.6, width * 0.994);
            if (walked >= nextSplit && depth > 0 && splits < 3) {
                splits++;
                double side = rnd.nextBoolean() ? 1 : -1;
                branch(x, y, ang + side * (0.5 + rnd.nextDouble() * 0.45), width * 0.66,
                        len * (0.35 + rnd.nextDouble() * 0.25), depth - 1, air, ox, oy);
                width *= 0.84;
                ang -= side * 0.12;
                nextSplit = walked + len * (0.18 + rnd.nextDouble() * 0.17);
            }
        }
    }

    static float[] vesselField(Runnable draw, int blur) {
        vesselCanvas = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        vg = vesselCanvas.createGraphics();
        vg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        vg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        vg.setColor(Color.WHITE);
        draw.run();
        vg.dispose();
        float[] f = new float[W * H];
        Raster r = vesselCanvas.getRaster();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) f[y * W + x] = r.getSample(x, y, 0) / 255f;
        if (blur > 0) boxBlur(f, blur);
        return f;
    }

    // ---------- render ----------
    static double[] K_SHELL = {0.12, 0.95, 2.6};
    static double[] K_ALBUMEN = {0.06, 0.30, 0.85};
    static double[] K_TISSUE = {0.55, 1.45, 2.2};
    static double[] K_BLOOD = {0.30, 2.0, 2.6};

    static void render(int stage, String out) throws IOException {
        seed = 1000L * stage + 7;
        rnd = new Random(seed);

        // air cell floor
        final Air air;
        if (stage == 1) air = x -> TOP + 118 + 16 * (1 - sq((x - CX) / A));
        else if (stage == 2) air = x -> TOP + 165 + 20 * (1 - sq((x - CX) / A)) + 6 * (x - CX) / A;
        else air = x -> TOP + 245 + 34 * (x - CX) / A + 10 * Math.sin((x - CX) / 47.0 + 1.3)
                + 26 * (fbm(x / 70.0, 0.5, 2, 41) - 0.5) + 14 * (1 - sq((x - CX) / A));

        // tissue density (yolk, embryo)
        float[] tissue = new float[W * H];
        double ex = CX - 12, ey = YW + 28; // embryo of stage 1
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double d = 0;
            if (stage == 1) {
                double yolk = Math.hypot((x - CX) / 175.0, (y - (YW + 55)) / 165.0);
                d += 0.32 * (1 - smoothstep(0.75, 1.05, yolk));
                double body = Math.hypot((x - ex) / 34.0, (y - ey) / 26.0);
                d += 1.1 * Math.exp(-body * body * 1.6);
                double eye = Math.hypot(x - (ex - 10), y - (ey - 6)) / 8.5;
                d += 3.6 * Math.exp(-eye * eye * 1.8);
            } else if (stage == 2) {
                double n = fbm(x / 70.0, y / 70.0, 4, 3) - 0.5;
                double m = Math.hypot((x - (CX + 18)) / 232.0, (y - (YW + 165)) / 255.0) + n * 0.28;
                d += 2.1 * (1 - smoothstep(0.78, 1.02, m));
                double core = Math.hypot((x - (CX + 30)) / 120.0, (y - (YW + 190)) / 140.0) + n * 0.3;
                d += 1.0 * (1 - smoothstep(0.5, 1.0, core));
                double eye = Math.hypot(x - (CX - 55), y - (YW + 60)) / 22.0;
                d += 1.0 * Math.exp(-eye * eye * 1.5);
            } else {
                double n = fbm(x / 60.0, y / 60.0, 4, 5) - 0.5;
                double below = y - air.y(x);
                d += 2.6 * smoothstep(-4, 70, below + n * 30);
                d += 0.7 * smoothstep(60, 260, below) * (0.7 + n);
            }
            tissue[y * W + x] = (float) d;
        }
        gauss(tissue, 3);

        // vessels
        float[] vessels = vesselField(() -> {
            if (stage == 1) {
                int trunks = 10;
                for (int i = 0; i < trunks; i++) {
                    double ang = i * 2 * Math.PI / trunks + rnd.nextGaussian() * 0.18;
                    branch(ex + Math.cos(ang) * 14, ey + Math.sin(ang) * 10, ang, 3.8, 170 + rnd.nextDouble() * 60, 3, air, ex, ey);
                }
                // sinus terminalis — the vascular boundary, a faint broken ring
                vg.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double prevX = 0, prevY = 0;
                for (int k = 0; k <= 360; k += 3) {
                    double t = Math.toRadians(k);
                    double rr = 205 + 14 * Math.sin(3 * t + 0.7) + 7 * Math.sin(7 * t);
                    double px = ex + Math.cos(t) * rr * 1.05, py = ey + Math.sin(t) * rr;
                    boolean ok = eggR(px, py) < 0.95 && py > air.y(px) + 6 && Math.sin(5 * t + 1) > -0.75;
                    if (k > 0 && ok) vg.draw(new Line2D.Double(prevX, prevY, px, py));
                    prevX = px; prevY = py;
                }
            } else if (stage == 2) {
                for (int i = 0; i < 13; i++) {
                    double t = Math.PI + 0.45 + (i + 0.5) * (Math.PI - 0.9) / 13 + rnd.nextGaussian() * 0.06;
                    double sx = CX + 18 + Math.cos(t) * 170, sy = YW + 165 + Math.sin(t) * 190;
                    double ang = t + rnd.nextGaussian() * 0.25;
                    branch(sx, sy, ang, 5.0, 150 + rnd.nextDouble() * 90, 4, air, CX + 18, YW + 165);
                }
            }
        }, 2);

        // air-cell membrane line
        float[] membrane = vesselField(() -> {
            vg.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            boolean started = false;
            for (int x = (int) (CX - A); x <= CX + A; x += 2) {
                double y = air.y(x);
                if (eggR(x, y) > 0.985) { started = false; continue; }
                if (!started) { p.moveTo(x, y); started = true; } else p.lineTo(x, y);
            }
            vg.draw(p);
        }, 3);

        double[] img = new double[W * H * 3];
        double lampX = CX, lampY = BOTTOM - 40;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            double r = eggR(x, y);
            if (r >= 1.0) continue;
            double depth = Math.sqrt(Math.max(0, 1 - r * r));
            // light: a lamp under the lower end plus the light spread through the whole egg
            double lamp = 1.05 + 1.5 * Math.exp(-(sq(x - lampX) + sq(y - lampY)) / (2 * sq(210)));
            // shell: grazing rays at the rim travel through more shell
            double mottle = fbm(x / 55.0, y / 55.0, 5, 11);
            double speck = fbm(x / 6.0, y / 6.0, 2, 23);
            double spots = smoothstep(0.62, 0.78, fbm(x / 26.0, y / 26.0, 3, 31)); // translucent patches
            double shell = (0.55 / Math.max(0.16, depth)) * (0.82 + 0.36 * mottle + 0.14 * (speck - 0.5)) * (1 - 0.22 * spots);
            boolean inAir = y < air.y(x);
            double albumen = inAir ? 0.08 : 0.55 + 0.75 * depth;
            double t = tissue[i];
            double b = vessels[i] * (stage == 3 ? 1.8 : stage == 2 ? 1.55 : 1.35);
            double m = membrane[i] * 0.9;
            double airGlow = inAir ? 1.28 : 1.0;
            for (int c = 0; c < 3; c++) {
                double absorb = K_SHELL[c] * shell + K_ALBUMEN[c] * albumen + K_TISSUE[c] * t + K_BLOOD[c] * b + K_TISSUE[c] * m * 0.5;
                double light = lamp * airGlow * Math.exp(-absorb);
                img[i * 3 + c] = light;
            }
            // soft rim: the edge of the shell fades into the dark instead of a cut-out
            double edge = smoothstep(1.0, 0.975, r);
            for (int c = 0; c < 3; c++) img[i * 3 + c] *= edge;
        }

        // bloom: light scattering around the egg and in the camera
        float[][] bloom = new float[3][W * H];
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < W * H; i++) bloom[c][i] = (float) img[i * 3 + c];
            gauss(bloom[c], 26);
        }
        float[][] bloomWide = new float[3][W * H];
        for (int c = 0; c < 3; c++) {
            System.arraycopy(bloom[c], 0, bloomWide[c], 0, W * H);
            gauss(bloomWide[c], 70);
        }

        BufferedImage outImg = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Random grain = new Random(seed + 99);
        double exposure = 1.9;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            int[] rgb = new int[3];
            double g = grain.nextGaussian() * 0.012;
            for (int c = 0; c < 3; c++) {
                double v = img[i * 3 + c] + 0.35 * bloom[c][i] + 0.45 * bloomWide[c][i];
                double mapped = 1 - Math.exp(-exposure * v);
                double val = BG[c] + (1 - BG[c]) * mapped + g;
                // gentle warm vignette in the far corners
                rgb[c] = (int) Math.round(255 * Math.max(0, Math.min(1, val)));
            }
            outImg.setRGB(x, y, (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
        }

        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.86f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File(out))) {
            w.setOutput(ios);
            w.write(null, new IIOImage(outImg, null, null), p);
        }
        w.dispose();
    }

    static double sq(double v) { return v * v; }

    static double smoothstep(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        for (int s = 1; s <= 3; s++) render(s, dir + "/img_candling_" + s + ".jpg");
    }
}
