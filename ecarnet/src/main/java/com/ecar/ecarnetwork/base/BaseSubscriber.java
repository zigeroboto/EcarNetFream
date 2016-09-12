package com.ecar.ecarnetwork.base;

import android.content.Context;
import android.net.ParseException;
import android.text.TextUtils;

import com.ecar.ecarnetwork.bean.ResBase;
import com.ecar.ecarnetwork.http.exception.CommonException;
import com.ecar.ecarnetwork.http.exception.InvalidException;
import com.ecar.ecarnetwork.http.exception.UserException;
import com.ecar.ecarnetwork.http.util.ConstantsLib;
import com.ecar.ecarnetwork.http.util.TagLibUtil;
import com.ecar.ecarnetwork.interfaces.security.IInvalid;
import com.google.gson.JsonParseException;

import org.json.JSONException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import retrofit2.adapter.rxjava.HttpException;
import rx.Subscriber;


/**
 * 订阅者基类：对异常统一处理
 * 目测权限处理可以放这里做
 */
public abstract class BaseSubscriber<T> extends Subscriber<T> {

    private Context context;
    private IInvalid iInvalid;
    public BaseSubscriber(Context context,IInvalid iInvalid) {
        this.context = context;
        this.iInvalid = iInvalid;
    }

    @Override
    public void onNext(T t) {
        if(t instanceof ResBase){
            ResBase base = (ResBase)t;
            if (base.state != 1) {//非成功
                this.onUserError(new CommonException(new UserException(base.code,base.msg,base)));
            }else {//if(base.state == 1)
                this.onUserSuccess(t);
            }
        }else{
            this.onOtherNext(t);
        }
    }

    protected void onOtherNext(T t){

    }

    protected abstract void onUserSuccess(T t);

    @Override
    public void onError(Throwable e) {
        CommonException ex = null;
        if (e instanceof UserException) {   // 用户自定义需要处理的异常
            /**
             * 1.自定义异常处理
             */
            UserException resultException = (UserException) e;
            ex = new CommonException(resultException);
            onUserError(ex);
        }else if(e instanceof InvalidException){
            /**
             * 2.非法异常处理：2.1 强制重新登录 2.2 校验错误 2.3 so on
             */
            InvalidException commonException = (InvalidException) e;
            if(InvalidException.FLAG_ERROR_RELOGIN.equals(commonException.getCode())){//重新登录
                showDialog(context,commonException.getMsg());
            }else if(InvalidException.FLAG_ERROR_RESPONCE_CHECK.equals(commonException.getCode())){//校验错误
                showMsg(context,commonException.getMsg());
            }
        } else if (e instanceof JsonParseException
                || e instanceof JSONException
                || e instanceof ParseException) {
            /**
             * 2.解析错误
             */
            ex = new CommonException(e, CommonException.FLAG_PARSE_ERROR);
            onUnifiedError(ex);   //
        } else if (e instanceof HttpException
                || e instanceof ConnectException
                || e instanceof SocketTimeoutException
                || e instanceof SocketException || e instanceof UnknownHostException) {
            /**
             * 3.网络错误
             */
            ex = new CommonException(e, CommonException.FLAG_NET_ERROR);
            onUnifiedError(ex);
        }else if(e instanceof SecurityException){
            /**
             * 4.权限未许可
             */
            ex = new CommonException(e, CommonException.FLAG_PERMISSION_ERROR);
            onUnifiedError(ex);
        }
        else {
            /**
             * 5.未知错误
             */
            ex = new CommonException(e, CommonException.FLAG_UNKNOWN);
            onUnifiedError(ex);   //未知错误
        }
        resetContext();
        if(ConstantsLib.DEBUG && ex!=null){
            e.printStackTrace();
            String errorMsg = ex.getMessage().toString();
            TagLibUtil.showLogError(BaseSubscriber.class.getSimpleName() + ": " + errorMsg);
            //bug写入日志
//            FileUtil.writeDebugTextFile(errorMsg);//不能简单的这么写，要考虑弄线程池处理
        }
    }

    @Override
    public void onCompleted() {
        resetContext();
    }

    //    protected abstract void onError(ApiException ex);
//    protected abstract void onResult1Error(ApiException ex);

    /**
     * 统一的 默认处理
     * 默认自动处理网络、解析、未知异常；弹提示
     * 有需要子类可以重写，不走super.onError 就可以屏蔽掉弹提示
     * @param ex
     */
    protected void onUnifiedError(CommonException ex){
        if(!ex.isDoNothing()){
            showMsg(context,ex.getMsg());
        }
    }

    /**
     * 自定义处理：
     * 接口返回 state 失败、自定义处理返回失败
     * 需要处理时 子类重写
     */
    protected void onUserError(CommonException ex){
        if(!ex.isDoNothing()){
            showMsg(context,ex.getMsg());
        }
    }


    /**
     * 上下文置空
     */
    private void resetContext(){
        context = null;
    }

    private static void showMsg(Context context,String msg){
        if(context!=null){
            String reMsg = TextUtils.isEmpty(msg) ? "" : msg;
            TagLibUtil.showToast(context,reMsg);
        }else{
            TagLibUtil.showLogDebug("Subscriber 上下文为空");
        }
    }

    /**
     *
     * @param msg
     */
    private void showDialog(final Context context,String msg) {
          if(iInvalid != null){
              iInvalid.reLogin(msg);
          }
    }
}