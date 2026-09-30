package dev.chronoflow;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class ChronoTile extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel("chrono-flow");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle("Notification panel");
        tile.updateTile();
    }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // PendingIntent overload is API 34+; the Intent overload remains valid below 34.
    @Override public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else startActivityAndCollapse(intent);
    }
}
