package com.hty.photomap;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.creable.ucmap.openGIS.UCCoordinateFilter;
import cn.creable.ucmap.openGIS.UCMapView;
import cn.creable.ucmap.openGIS.UCMarker;
import cn.creable.ucmap.openGIS.UCMarkerLayer;
import cn.creable.ucmap.openGIS.UCMarkerLayerListener;
import cn.creable.ucmap.openGIS.UCRasterLayer;
import cn.creable.ucmap.openGIS.UCVectorLayer;

public class MapActivity extends AppCompatActivity {

    TextView textView, textView_info;
    SimpleDateFormat SDF = new SimpleDateFormat("yyyy年MM月dd日");
    SimpleDateFormat SDF_time = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
    UCMapView mapView;
    UCVectorLayer VLayer;
    UCRasterLayer RLayer;
    UCMarkerLayer MLayer;
    UCMarker marker;
    GeometryFactory GF = new GeometryFactory();
    int maptype = 0;
    List<Coordinate> list_coordinate;
    Coordinate[] coords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        textView = findViewById(R.id.textView);
        textView_info = findViewById(R.id.textView_info);
        ImageButton imageButton_location = findViewById(R.id.imageButton_location);
        imageButton_location.setOnClickListener(new ClickListener());
        Intent intent = getIntent();
        String sdate = intent.getStringExtra("date");
        Date date = null;
        try {
            date = SDF.parse(sdate);
        } catch (Exception e) {
        }
        long ldate = date.getTime();
        long ldate1 = ldate + 24*60*60*1000;

        //query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
        String[] projection = new String[]{ MediaStore.Images.Media.DATA , MediaStore.Images.Media.DATE_TAKEN };
        String selection = MediaStore.Images.Media.DATE_TAKEN + ">=? and " + MediaStore.Images.Media.DATE_TAKEN + "<?";
        String[] selectionArgs =  new String[]{ String.valueOf(ldate), String.valueOf(ldate1) };
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
        list_coordinate = new ArrayList<>();
        final List<String> list_image_path = new ArrayList<>();
        final List<String> list_time = new ArrayList<>();
        while (cursor.moveToNext()) {
            String imagePath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            long date_taken = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN));
            date = new Date(date_taken);
            String stime = SDF_time.format(date);
            try {
                //EXIF的GPS数据转经纬度，https://blog.csdn.net/diyangxia/article/details/50995253
                ExifInterface exifInterface = new ExifInterface(imagePath);
                String latValue = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
                String lngValue = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
                String latRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
                String lngRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
                if(latValue != null && lngValue != null && latRef != null && lngRef != null) {
                    Coordinate coordinate = new Coordinate(rationalToDouble(lngValue, lngRef), rationalToDouble(latValue, latRef));
                    list_coordinate.add(coordinate);
                    list_image_path.add(imagePath);
                    list_time.add(stime);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        cursor.close();
        textView.setText(sdate + " " + list_coordinate.size() + " 个点");

        mapView = findViewById(R.id.mapView);
        mapView.rotation(false);
        mapView.addScaleBar();
        mapView.moveTo(118.778771, 32.043880, 5000);

        String dir = Environment.getExternalStorageDirectory().getPath();
        RLayer = mapView.addGoogleMapLayer("http://mt0.google.cn/vt/lyrs=m&hl=zh-CN&gl=cn&scale=2&x={X}&y={Y}&z={Z}", 0, 20, dir + "/cacheGoogleMapM.db");
        if (VLayer == null) VLayer = mapView.addVectorLayer();
        if (MLayer == null) MLayer = mapView.addMarkerLayer(new UCMarkerLayerListener() {
            @Override
            public boolean onItemSingleTapUp(int index, String title, String description, double x, double y) {
                if(!title.equals("")) {
                    textView_info.setText(title + "\n经度：" + x + "\n纬度：" + y + "\n" + description);
                    int i = Integer.parseInt(title);
                    Gps gps = PositionUtil.gcj_To_Gps84(y, x);
                    marker.setXY(gps.getWgLon(), gps.getWgLat());
                    MLayer.refresh();
                    ImageView imageView = new ImageView(MapActivity.this);
                    Uri uri = Uri.fromFile(new File(list_image_path.get(i)));
                    imageView.setImageURI(uri);
                    new AlertDialog.Builder(MapActivity.this).setTitle(list_time.get(i)).setView(imageView).setPositiveButton("确定", null).show();
                }
                return true;
            }
            @Override
            public boolean onItemLongPress(int index, String title, String description, double x, double y) {
                Toast.makeText(getBaseContext(), title + ", "+ description, Toast.LENGTH_SHORT).show();
                textView_info.setText(title + "\n经度：" + x + "\n纬度：" + y);
                return false;
            }
        });

        mapView.setCoordinateFilter(filter_WGS_to_GCJ);
        Log.e(Thread.currentThread().getStackTrace()[2] + "", "坐标数目：" + list_coordinate.size());
        if (list_coordinate.size() > 1) {
            coords = list_coordinate.toArray(new Coordinate[list_coordinate.size()]);
            Geometry geometry = GF.createLineString(coords);
            VLayer.addLine(geometry, 2, 0xFF0000FF);
            mapView.moveTo(coords[coords.length / 2].x, coords[coords.length / 2].y, mapView.getScale());
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker);
            marker = MLayer.addBitmapItem(bitmap, coords[0].x, coords[0].y, "", "");

            for (int i = 0; i < coords.length; i++) {
                if (i == 0) {
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker_start);
                } else if (i == coords.length - 1) {
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker_end);
                } else {
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker_point);
                }
                MLayer.addBitmapItem(bitmap, coords[i].x, coords[i].y, "" + i, list_image_path.get(i));
            }
        }else{
            textView_info.setText("没有有效的GPS坐标！");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "线路图");
        menu.add(0, 1, 1, "地形图");
        menu.add(0, 2, 2, "卫星图");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 0) {
            if (maptype != 0) {
                mapView.deleteLayer(RLayer);
                String dir = Environment.getExternalStorageDirectory().getPath();
                RLayer = mapView.addGoogleMapLayer("http://mt0.google.cn/vt/lyrs=m&hl=zh-CN&gl=cn&scale=2&x={X}&y={Y}&z={Z}", 0, 20, dir + "/cacheGoogleMapM.db");
                mapView.moveLayer(RLayer, 0);
                mapView.refresh();
                maptype = 0;
            }
        } else if (id == 1) {
            if (maptype != 1) {
                mapView.deleteLayer(RLayer);
                String dir = Environment.getExternalStorageDirectory().getPath();
                RLayer = mapView.addGoogleMapLayer("http://mt0.google.cn/vt/lyrs=p&hl=zh-CN&gl=cn&scale=2&x={X}&y={Y}&z={Z}", 0, 20, dir + "/cacheGoogleMapP.db");
                mapView.moveLayer(RLayer, 0);
                mapView.refresh();
                maptype = 1;
            }
        } else if (id == 2) {
            if (maptype != 2) {
                mapView.deleteLayer(RLayer);
                String dir = Environment.getExternalStorageDirectory().getPath();
                RLayer = mapView.addGoogleMapLayer("http://mt0.google.cn/vt/lyrs=y&hl=zh-CN&gl=cn&scale=2&x={X}&y={Y}&z={Z}", 0, 20, dir + "/cacheGoogleMapY.db");
                mapView.moveLayer(RLayer, 0);
                mapView.refresh();
                maptype = 2;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    double rationalToDouble(String rational, String ref) {
        String[] parts = rational.split(",");
        String[] pair;
        pair = parts[0].split("/");
        double degrees = Double.parseDouble(pair[0].trim()) / Double.parseDouble(pair[1].trim());
        pair = parts[1].split("/");
        double minutes = Double.parseDouble(pair[0].trim()) / Double.parseDouble(pair[1].trim());
        pair = parts[2].split("/");
        double seconds = Double.parseDouble(pair[0].trim()) / Double.parseDouble(pair[1].trim());
        double result = degrees + (minutes / 60.0) + (seconds / 3600.0);
        if ((ref.equals("S") || ref.equals("W"))) {
            return -result;
        }
        return result;
    }

    UCCoordinateFilter filter_WGS_to_GCJ = new UCCoordinateFilter() {
        @Override
        public double[] to(double x, double y) {
            double[] result = new double[2];
            Gps gps = PositionUtil.gps84_To_Gcj02(y, x);
            result[0] = gps.getWgLon();
            result[1] = gps.getWgLat();
            return result;
        }

        @Override
        public double[] from(double x, double y) {
            double[] result = new double[2];
            Gps gps = PositionUtil.gcj_To_Gps84(y, x);
            result[0] = gps.getWgLon();
            result[1] = gps.getWgLat();
            return result;
        }
    };

    class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.imageButton_location) {
                if (list_coordinate.size() > 1) {
                    mapView.moveTo(coords[coords.length / 2].x, coords[coords.length / 2].y, mapView.getScale());
                }
            }
        }
    }

}