package com.android.launcher3.custom;

import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.util.GridOccupancy;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves home screen items out of the area used by the first page pinned item (search bar and
 * glance panel) before the workspace is loaded. Without this the loader drops them as
 * overlapping, e.g. when the search bar is turned back on or the glance panel grows.
 *
 * Items stay on the first page if there is room, otherwise they go to the next page with space
 * or to a new page.
 */
public final class PinnedAreaRelocator {

    private static final String TAG = "PinnedAreaRelocator";

    private PinnedAreaRelocator() { }

    @WorkerThread
    public static void relocate(ModelDbController db, InvariantDeviceProfile idp,
            int pinnedRow, int pinnedRows) {
        int pinnedColumns = idp.numSearchContainerColumns;
        SparseArray<GridOccupancy> screens = new SparseArray<>();
        List<int[]> blocking = new ArrayList<>(); // {id, spanX, spanY}

        String[] columns = {Favorites._ID, Favorites.SCREEN, Favorites.CELLX, Favorites.CELLY,
                Favorites.SPANX, Favorites.SPANY};
        try (Cursor c = db.query(TABLE_NAME, columns,
                Favorites.CONTAINER + "=" + Favorites.CONTAINER_DESKTOP, null, null)) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                int screen = c.getInt(1);
                int x = c.getInt(2);
                int y = c.getInt(3);
                int spanX = Math.max(1, c.getInt(4));
                int spanY = Math.max(1, c.getInt(5));
                // Overlaps the pinned area [pinnedRow, pinnedRow + pinnedRows) x [0, columns)?
                if (screen == Workspace.FIRST_SCREEN_ID && x < pinnedColumns
                        && y < pinnedRow + pinnedRows && y + spanY > pinnedRow) {
                    blocking.add(new int[] {id, spanX, spanY});
                } else {
                    occupancy(screens, screen, idp).markCells(x, y, spanX, spanY, true);
                }
            }
        }
        if (blocking.isEmpty()) {
            return;
        }

        occupancy(screens, Workspace.FIRST_SCREEN_ID, idp)
                .markCells(0, pinnedRow, pinnedColumns, pinnedRows, true);
        int[] cell = new int[2];
        for (int[] item : blocking) {
            int targetScreen = -1;
            for (int i = 0; i < screens.size(); i++) {
                if (screens.valueAt(i).findVacantCell(cell, item[1], item[2])) {
                    targetScreen = screens.keyAt(i);
                    break;
                }
            }
            if (targetScreen < 0) {
                targetScreen = screens.keyAt(screens.size() - 1) + 1;
                cell[0] = 0;
                cell[1] = 0;
            }
            occupancy(screens, targetScreen, idp).markCells(cell[0], cell[1], item[1], item[2], true);

            ContentValues values = new ContentValues();
            values.put(Favorites.SCREEN, targetScreen);
            values.put(Favorites.CELLX, cell[0]);
            values.put(Favorites.CELLY, cell[1]);
            db.update(TABLE_NAME, values, Favorites._ID + "=" + item[0], null);
            Log.d(TAG, "Moved item " + item[0] + " to screen " + targetScreen
                    + " (" + cell[0] + "," + cell[1] + ")");
        }
    }

    private static GridOccupancy occupancy(SparseArray<GridOccupancy> screens, int screen,
            InvariantDeviceProfile idp) {
        GridOccupancy occupancy = screens.get(screen);
        if (occupancy == null) {
            occupancy = new GridOccupancy(idp.numColumns, idp.numRows);
            screens.put(screen, occupancy);
        }
        return occupancy;
    }
}
