package org.jeecg.modules.bonus.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtil {

    private static SimpleDateFormat dft = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 变动时间
     * @param oriDate
     * @param time
     * @param num
     * @return
     */
    public static String changeTime(Date oriDate , Integer time , Integer num){

        String finalTime = null;

        try {
            Calendar date = Calendar.getInstance();
            date.setTime(oriDate);
            date.add(time, num);
            //
            finalTime = dft.format(date.getTime());
        }catch (Exception e){
            e.printStackTrace();
        }

        return finalTime;
    }


    /**
     * 变动时间
     * @param oriDate
     * @param time
     * @param num
     * @return
     */
    public static String changeTime(String oriDate , Integer time , Integer num){

        String finalTime = null;
        try {

            Date parse = dft.parse(oriDate);
            Calendar date = Calendar.getInstance();
            date.setTime(parse);
            date.add(time, num);
            //
            finalTime =  dft.format(date.getTime());
        }catch (Exception e){
            e.printStackTrace();
        }

        //
        return finalTime;
    }
}