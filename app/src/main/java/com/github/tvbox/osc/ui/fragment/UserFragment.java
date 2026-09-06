package com.github.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.UA;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class UserFragment extends BaseLazyFragment {
    public static HomeHotVodAdapter homeHotVodAdapter;
    private List<Movie.Video> homeSourceRec;
    public static TvRecyclerView tvHotList;
    private SourceViewModel sourceViewModel;
    private ImageView ivPreviewPoster;
    private TextView tvPreviewName;
    private TextView tvPreviewTypeYear;
    private TextView tvPreviewActor;
    private TextView tvPreviewDes;

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        tvHotList.setVisibility(View.VISIBLE);
        tvHotList.setHasFixedSize(true);
        // 卡片始终横向排列在页面底部
        tvHotList.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.HORIZONTAL, false));
        int paddingLeft = -tvHotList.mHorizontalSpacingWithMargins / 2 + getResources().getDimensionPixelSize(R.dimen.vs_6);
        int paddingTop = getResources().getDimensionPixelSize(R.dimen.vs_10);
        int paddingRight = -tvHotList.mHorizontalSpacingWithMargins / 2 + getResources().getDimensionPixelSize(R.dimen.vs_6);
        int paddingBottom = getResources().getDimensionPixelSize(R.dimen.vs_10);
        tvHotList.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(20);
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vod.note = "上次看到" + vodInfo.playNote;
                vodList.add(vod);
            }
            homeHotVodAdapter.setNewData(vodList);
            showFirstPreview(homeHotVodAdapter);
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TvRecyclerView hotList = view.findViewById(R.id.tvHotList);
        if (hotList != null && hotList.getLayoutManager() == null) {
            hotList.setLayoutManager(new V7LinearLayoutManager(mContext, V7LinearLayoutManager.HORIZONTAL, false));
        }
    }

    private void jumpSearch(Movie.Video vod) {
        Intent newIntent;
        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
            newIntent = new Intent(mContext, FastSearchActivity.class);
        } else {
            newIntent = new Intent(mContext, SearchActivity.class);
        }
        newIntent.putExtra("title", vod.name);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mActivity.startActivity(newIntent);
    }

    private ImgUtil.Style style;
    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        tvHotList = findViewById(R.id.tvHotList);
        ivPreviewPoster = findViewById(R.id.ivPreviewPoster);
        tvPreviewName = findViewById(R.id.tvPreviewName);
        tvPreviewTypeYear = findViewById(R.id.tvPreviewTypeYear);
        tvPreviewActor = findViewById(R.id.tvPreviewActor);
        tvPreviewDes = findViewById(R.id.tvPreviewDes);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && homeSourceRec!=null) {
            style=ImgUtil.initStyle();
        }
        String tvRate="";
        if(Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 0){
            tvRate="豆瓣热播";
        }else if(Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1){
          tvRate= homeSourceRec!=null?"站点推荐":"豆瓣热播";
        }
        homeHotVodAdapter = new HomeHotVodAdapter(style,tvRate);
        homeHotVodAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty())
                    return;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));

                if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && homeSourceRec != null && vod.action != null) {
                    sourceViewModel.action(vod.sourceKey, vod.action);
                    return;
                }

                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) && HawkConfig.hotVodDelete) {
                    homeHotVodAdapter.remove(position);
                    VodInfo vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
                    assert vodInfo != null;
                    RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo);
                    Toast.makeText(mContext, "已删除当前记录", Toast.LENGTH_SHORT).show();
                } else if (vod.id != null && vod.id.startsWith("msearch:")) {
                    jumpSearch(vod);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    bundle.putString("title", vod.name);
                    bundle.putString("picture", vod.pic);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });
        
        homeHotVodAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty()) return false;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                assert vod != null;
                if (vod.id != null && vod.id.startsWith("msearch:")) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    bundle.putString("title", vod.name);
                    bundle.putString("picture", vod.pic);
                    jumpActivity(DetailActivity.class, bundle);
                } else if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2)) {
                    HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
                    homeHotVodAdapter.notifyDataSetChanged();
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);                    
                }
                return true;
            }    
        });

        tvHotList.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                Movie.Video vod = homeHotVodAdapter.getItem(position);
                if (vod != null) {
                    updatePreview(vod);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        tvHotList.setAdapter(homeHotVodAdapter);

        initHomeHotVod(homeHotVodAdapter);
        sourceViewModel.actionResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject jsonObject) {
                if (jsonObject == null) return;
                String msg = jsonObject.optString("msg");
                if (!msg.isEmpty()) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                showFirstPreview(adapter);
                return;
            }
        } else if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            showFirstPreview(adapter);
            return;
        }
        setDouBanData(adapter);
    }

    /**
     * 默认预览第一个影片信息，并将焦点落在第一个卡片上
     */
    private void showFirstPreview(HomeHotVodAdapter adapter) {
        if (adapter != null && adapter.getData() != null && adapter.getData().size() > 0) {
            Movie.Video first = adapter.getData().get(0);
            if (first != null) {
                updatePreview(first);
            }
            // 默认选中第一个卡片
            tvHotList.post(new Runnable() {
                @Override
                public void run() {
                    View firstChild = tvHotList.getLayoutManager().findViewByPosition(0);
                    if (firstChild != null) {
                        firstChild.requestFocus();
                    } else {
                        tvHotList.requestFocus();
                    }
                }
            });
        }
    }

    /**
     * 更新大封面预览区
     */
    private void updatePreview(Movie.Video vod) {
        if (vod == null) return;
        // 海报（大背景，带轻微虚化效果）
        String pic = vod.pic == null ? "" : vod.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivPreviewPoster.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
            } else {
                ImgUtil.loadBlur(pic, ivPreviewPoster, 12);
            }
        }
        // 名称
        tvPreviewName.setText(vod.name == null ? "" : vod.name);
        // 类型 + 年份
        StringBuilder typeYear = new StringBuilder();
        if (!TextUtils.isEmpty(vod.type)) {
            typeYear.append(vod.type);
        }
        if (vod.year > 0) {
            if (typeYear.length() > 0) typeYear.append(" · ");
            typeYear.append(vod.year);
        }
        if (typeYear.length() == 0 && !TextUtils.isEmpty(vod.note)) {
            typeYear.append(vod.note);
        }
        tvPreviewTypeYear.setText(typeYear.toString());
        // 主演
        String actorText = "";
        if (!TextUtils.isEmpty(vod.actor)) {
            actorText = "主演：" + vod.actor;
        }
        tvPreviewActor.setText(actorText);
        // 简介
        tvPreviewDes.setText(vod.des == null ? "" : vod.des);
    }

    private void setDouBanData(HomeHotVodAdapter adapter) {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        adapter.setNewData(hotMovies);
                        showFirstPreview(adapter);
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.setNewData(loadHots(netJson));
                                    showFirstPreview(adapter);
                                }
                            });
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            int limit = Math.min(array.size(), 25);
            for (int i = 0; i < limit; i++) {
                JsonElement ele = array.get(i);
                JsonObject obj = ele.getAsJsonObject();
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                if (!vod.note.isEmpty()) vod.note += " 分";
                vod.pic = obj.get("cover").getAsString()
                        + "@User-Agent=" + UA.randomOne()
                        + "@Referer=https://www.douban.com/";
                result.add(vod);
            }
        } catch (Throwable th) {
        }
        return result;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_CONNECTION) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
