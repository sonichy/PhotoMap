package com.hty.photomap;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.provider.MediaStore;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    SimpleDateFormat SDF = new SimpleDateFormat("yyyy年MM月dd日");
    List<String> list_date = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LinearLayout linearLayout = findViewById(R.id.linearLayout);

        //query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
        String[] projection = new String[]{ MediaStore.Images.Media.DATE_TAKEN };
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC");
        while (cursor.moveToNext()) {
            long date_taken = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN));
            Date date = new Date(date_taken);
            list_date.add(SDF.format(date));
        }
        cursor.close();

        //统计
        Map<String, Integer> map = new HashMap<>();
        for (int i=0; i<list_date.size(); i++) {
            Integer integer = map.get(list_date.get(i));
            map.put(list_date.get(i), integer == null?1:integer+1);
        }

        //排序
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> map1, Map.Entry<String, Integer> map2) {
                return map2.getKey().compareTo(map1.getKey());
            }
        });

        //遍历
        for (Map.Entry<String, Integer> map1 : list) {
            System.out.println(map1.getKey() + " ：" + map1.getValue());
            int i = map1.getValue();
            if(i > 1) {
                Button button = new Button(this);
                button.setText(map1.getKey() + " (" + map1.getValue() + ")");
                button.setHint(map1.getKey());
                button.setOnClickListener(new ClickListener());
                linearLayout.addView(button);
            }
        }

    }

    class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Button button = (Button) v;
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            intent.putExtra("date", button.getHint());
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "关于");
        menu.add(0, 1, 1, "设置");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 0) {
            new AlertDialog.Builder(MainActivity.this).setIcon(R.mipmap.ic_launcher).setTitle("照片地图 V1.0").setMessage("媒体库图片按日期分类，点击日期把这一天的图片的GPS位置在地图上标记并连线，点击标记点显示照片。\n主页：http://github.com/sonichy/PhotoMap\n作者：黄颖\nE-mail：sonichy@163.com\nQQ：84429027\n\n地图工具：UCMap\n\n参考文献：\n坐标转换：https://blog.csdn.net/ma969070578/article/details/41013547\n\nEXIF的GPS数据转经纬度：https://blog.csdn.net/diyangxia/article/details/50995253\n\n更新历史\n1.0 (2019-10)\n实现基本功能。").setPositiveButton("确定", null).show();
        } else if (id == 1) {

        }
        return super.onOptionsItemSelected(item);
    }

}