import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

public class CustomMarkerView extends MarkerView {

    private final TextView tvMarker;

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvMarker = findViewById(R.id.tvMarker);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        tvMarker.setText("Tiempo: " + (int)e.getX()/1000 + "s\nNivel: " + (int)e.getY() + " dB");
        super.refreshContent(e, highlight);
    }
}

