package com.zhglxt.common.utils;

import com.alibaba.fastjson.JSONObject;
import com.zhglxt.common.config.GlobalConfig;
import com.zhglxt.common.constant.Constants;
import com.zhglxt.common.ipLocation.Searcher;
import com.zhglxt.common.utils.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 获取地址类
 *
 * @author ruoyi
 */
public class AddressUtils {
    private static final Logger log = LoggerFactory.getLogger(AddressUtils.class);

    // IP地址查询
    public static final String IP_URL = "https://whois.pconline.com.cn/ipJson.jsp";

    // 未知地址
    public static final String UNKNOWN = "XX XX";

    public static String getRealAddressByIP(String ip) {
        // 内网不查询
        if (IpUtils.internalIp(ip)) {
            return "内网IP";
        }
        if (GlobalConfig.isAddressEnabled()) {
            try {
                String rspStr = HttpUtils.sendGet(IP_URL, "ip=" + ip + "&json=true", Constants.GBK);
                if (StringUtils.isEmpty(rspStr)) {
                    log.error("获取地理位置异常 {}", ip);
                    return UNKNOWN;
                }
                JSONObject obj = JSONObject.parseObject(rspStr);
                String region = obj.getString("pro");
                String city = obj.getString("city");
                return String.format("%s %s", region, city);
            } catch (Exception e) {
                log.error("获取地理位置异常 {}", e);
            }
        }
        return UNKNOWN;
    }

    public static String getRealAddressByIp2region(String ip) {
        // 内网不查询
        if (IpUtils.internalIp(ip))
        {
            return "内网IP";
        }
        if (GlobalConfig.isAddressEnabled())
        {
            try {
                // 使用类加载器找文件，无需关心实际物理路径
                InputStream inputStream = AddressUtils.class.getClassLoader()
                        .getResourceAsStream("ipdb/ip2region.xdb");
                // 如果需要转成文件（某些库要求文件路径时可用临时文件）
                if (inputStream != null) {
                    // 创建临时文件
                    File tempFile = File.createTempFile("ip2region", ".xdb");
                    // JVM 退出时删除
                    tempFile.deleteOnExit();
                    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    String dbPath = tempFile.getAbsolutePath();

                    // ---- 高性能查询方式（预加载索引） ----
                    // 加载整个索引到内存中
                    byte[] indexBuff = Files.readAllBytes(Paths.get(dbPath));
                    Searcher memorySearcher = Searcher.newWithBuffer(indexBuff);

                    // 执行内存查询，速度更快（适用于高并发场景）
                    String memoryRegion = memorySearcher.search(ip);
                    List<String> validParts = getStrings(memoryRegion);

                    // 释放资源
                    memorySearcher.close();
                    // 使用连字符拼接有效字段
                    return String.join("-", validParts);
                } else {
                    // 处理文件找不到的情况，比如抛异常或返回默认值
                    throw new RuntimeException("ip2region.xdb 未找到，请检查 resources/ipdb 目录");
                }
            } catch (Exception e) {
                log.error("获取地理位置异常 {}", e);
            }
        }
        return UNKNOWN;
    }

    private static List<String> getStrings(String memoryRegion) {
        String[] regionArray = memoryRegion.split("\\|");
        List<String> validParts = new ArrayList<>();
        // 依次检查每个字段，跳过值为"0"的字段
        if (regionArray.length >= 1 && !"0".equals(regionArray[0])) {
            validParts.add(regionArray[0]); // 国家
        }
        if (regionArray.length >= 3 && !"0".equals(regionArray[2])) {
            validParts.add(regionArray[2]); // 省份
        }
        if (regionArray.length >= 4 && !"0".equals(regionArray[3])) {
            validParts.add(regionArray[3]); // 城市
        }
        if (regionArray.length >= 5 && !"0".equals(regionArray[4])) {
            validParts.add(regionArray[4]); // 网络服务提供商
        }
        return validParts;
    }
}
