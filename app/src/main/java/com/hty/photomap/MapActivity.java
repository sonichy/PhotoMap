package com.hty.photomap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;

import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MapActivity extends Activity {

    TextView textView, textView_info;
    SimpleDateFormat SDF = new SimpleDateFormat("yyyy年MM月dd日");
    SimpleDateFormat SDF_time = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
    String dir;

    List<LatLng> list_LatLng = new ArrayList<LatLng>();
    MapView mMapView = null;
    BaiduMap mBaiduMap;
    BitmapDescriptor bitmap1, bitmap2, bitmap3, bitmap4;
    Marker marker1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_map);
        dir = Environment.getExternalStorageDirectory().getPath();
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(new MapStatus.Builder().zoom(16).build()));
        mBaiduMap.setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                Bundle extraInfo = marker.getExtraInfo();
                int index = extraInfo.getInt("index");
                String time = extraInfo.getString("time");
                String image_path = extraInfo.getString("image_path");
                ImageView imageView = new ImageView(MapActivity.this);
                Uri uri = Uri.fromFile(new File(image_path));
                imageView.setImageURI(uri);
                new AlertDialog.Builder(MapActivity.this).setTitle(index + ":" + time).setView(imageView).setPositiveButton("确定", null).show();
                textView_info.setText(index + "\n经度：" + marker.getPosition().latitude + "\n纬度：" + marker.getPosition().longitude);
                marker1.setPosition(new LatLng(marker.getPosition().latitude, marker.getPosition().longitude));
                return false;
            }
        });
        UiSettings settings = mBaiduMap.getUiSettings();
        settings.setRotateGesturesEnabled(false);
        settings.setOverlookingGesturesEnabled(false);
        textView = (TextView) findViewById(R.id.textView);
        textView_info = (TextView) findViewById(R.id.textView_info);
        ImageButton imageButton_location = (ImageButton) findViewById(R.id.imageButton_location);
        imageButton_location.setOnClickListener(new ClickListener());
        bitmap1 = BitmapDescriptorFactory.fromResource(R.drawable.marker_start);
        bitmap2 = BitmapDescriptorFactory.fromResource(R.drawable.marker_end);
        bitmap3 = BitmapDescriptorFactory.fromResource(R.drawable.marker_point);
        bitmap4 = BitmapDescriptorFactory.fromResource(R.drawable.marker);
        OverlayOptions option = new MarkerOptions().position(new LatLng(0,0)).icon(bitmap4);
        marker1 = (Marker) mBaiduMap.addOverlay(option);

        Intent intent = getIntent();
        String sdate = intent.getStringExtra("date");
        Date date = null;
        try {
            date = SDF.parse(sdate);
        } catch (Exception e) {
            Log.e(Thread.currentThread().getStackTrace()[2] + "", e.toString());
        }
        long ldate = date.getTime();
        long ldate1 = ldate + 24*60*60*1000;

        //query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
        String[] projection = new String[]{ MediaStore.Images.Media.DATA , MediaStore.Images.Media.DATE_TAKEN };
        String selection = MediaStore.Images.Media.DATE_TAKEN + ">=? and " + MediaStore.Images.Media.DATE_TAKEN + "<?";
        String[] selectionArgs =  new String[]{ String.valueOf(ldate), String.valueOf(ldate1) };
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);

        final List<String> list_image_path = new ArrayList<String>();
        final List<String> list_time = new ArrayList<String>();
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
                if (latValue != null && lngValue != null && latRef != null && lngRef != null) {
                    LatLng latLng = new LatLng(rationalToDouble(latValue, latRef), rationalToDouble(lngValue, lngRef));
                    CoordinateConverter converter = new CoordinateConverter();
                    converter.from(CoordinateConverter.CoordType.GPS);
                    converter.coord(latLng);
                    latLng = converter.convert();
                    list_LatLng.add(latLng);
                    list_image_path.add(imagePath);
                    list_time.add(stime);
                }
            } catch (Exception e) {
                Log.e(Thread.currentThread().getStackTrace()[2] + "", e.toString());
            }
        }
        cursor.close();
        textView.setText(sdate + " " + list_LatLng.size() + " 个点");
        if (list_LatLng.size() > 1) {
            OverlayOptions OO_polyline = new PolylineOptions().width(4).color(0xA00000FF).points(list_LatLng);
            mBaiduMap.addOverlay(OO_polyline);
            for (int i = 0; i < list_LatLng.size(); i++) {
                OverlayOptions options;
                Bundle bundle = new Bundle();
                bundle.putInt("index", i);
                bundle.putString("time", list_time.get(i));
                bundle.putString("image_path", list_image_path.get(i));
                if (i == 0) {
                    options = new MarkerOptions().position(list_LatLng.get(0)).icon(bitmap1).extraInfo(bundle);
                } else if (i == list_LatLng.size() - 1) {
                    options = new MarkerOptions().position(list_LatLng.get(list_LatLng.size() - 1)).icon(bitmap2).extraInfo(bundle);
                } else {
                    options = new MarkerOptions().position(list_LatLng.get(i)).icon(bitmap3).extraInfo(bundle);
                }
                mBaiduMap.addOverlay(options);
            }
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(list_LatLng.get(list_LatLng.size() / 2)));
        } else {
            textView_info.setText("没有有效的GPS坐标！");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "线路图");
        menu.add(0, 1, 1, "卫星图");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){
            case 0:
                mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
                break;
            case 1:
                mBaiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.imageButton_location) {
                if (list_LatLng.size() > 1) {
                    mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(list_LatLng.get(list_LatLng.size() / 2)));
                }
            }
        }
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

}