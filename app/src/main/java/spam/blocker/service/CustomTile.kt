package spam.blocker.service

import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import spam.blocker.G
import spam.blocker.R
import spam.blocker.util.spf

// A quick settings tile that toggles the global switch.
class CustomTile : TileService() {

    private fun update() {
        val enabled = spf.Global(this).isGloballyEnabled
        qsTile.state = if (enabled) STATE_ACTIVE else STATE_INACTIVE
        qsTile.label = getString(R.string.app_name)
        qsTile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()

        val spf = spf.Global(this)
        val enabled = !spf.isGloballyEnabled
        spf.isGloballyEnabled = enabled

        G.globallyEnabled.value = enabled

        update()
    }
}
