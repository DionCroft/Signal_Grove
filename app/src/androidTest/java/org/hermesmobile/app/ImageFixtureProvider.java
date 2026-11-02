package org.hermesmobile.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicInteger;

/** Test APK only: generated pixels, no application dependencies or real files. */
public final class ImageFixtureProvider extends ContentProvider {
    private final AtomicInteger active = new AtomicInteger();
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String order) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle(); result.putInt("active", active.get()); return result;
    }
    @Override public ParcelFileDescriptor openFile(final Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only fixture");
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe(); active.incrementAndGet();
            Thread writer = new Thread(new Runnable() { @Override public void run() {
                try {
                    if ("slow".equals(uri.getLastPathSegment())) Thread.sleep(1500);
                    byte[] bytes;
                    if ("bad".equals(uri.getLastPathSegment())) bytes = new byte[] {1, 2, 3};
                    else {
                        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor("blue".equals(uri.getLastPathSegment()) ? Color.BLUE : Color.RED);
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); bytes = output.toByteArray();
                    }
                    try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) { output.write(bytes); }
                } catch (Exception ignored) { try { pipe[1].close(); } catch (Exception alsoIgnored) {} }
                finally { active.decrementAndGet(); }
            }}, "HermesImageFixture");
            writer.setDaemon(true); writer.start(); return pipe[0];
        } catch (java.io.IOException error) { throw new FileNotFoundException("Fixture pipe unavailable"); }
    }
}
