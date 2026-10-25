package cn.yueji.balance;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Bounds the entire operation, including DNS and multiple requests, not only an individual socket read. */
final class NetworkIo {
    static final long TIMEOUT_MILLIS=18000;
    static <T> T call(Callable<T> operation)throws Exception{return call(operation,TIMEOUT_MILLIS);}
    static <T> T call(Callable<T> operation,long timeoutMillis)throws Exception{
        FutureTask<T> task=new FutureTask<>(operation);
        Thread worker=new Thread(task,"bounded-network");worker.setDaemon(true);worker.start();
        try{return task.get(timeoutMillis,TimeUnit.MILLISECONDS);}
        catch(TimeoutException e){task.cancel(true);throw new SocketTimeoutException("网络连接超时");}
        catch(InterruptedException e){task.cancel(true);Thread.currentThread().interrupt();throw new InterruptedIOException("网络操作已取消");}
        catch(ExecutionException e){
            Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;
            throw new IOException("数据服务暂不可用",cause);
        }
    }
    static boolean connectivity(Throwable failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof IOException||cause instanceof TimeoutException)return true;
        return false;
    }
    static String message(Throwable failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
            if(cause instanceof SocketTimeoutException||cause instanceof TimeoutException)return "网络连接超时，请检查网络后重试。";
            if(cause instanceof UnknownHostException||cause instanceof ConnectException||cause instanceof NoRouteToHostException)return "无法连接网络，请检查网络连接后重试。";
        }
        if(connectivity(failure))return "网络或数据服务暂不可用，请检查网络后重试。";
        if(failure instanceof IllegalArgumentException)return failure.getMessage()==null?"输入内容无效，请检查后重试。":failure.getMessage();
        return "数据服务返回异常，请稍后重试。";
    }
    private NetworkIo(){}
}
