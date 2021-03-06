package com.dianrong.common.uniauth.server.synchronous.hr.support;

import com.dianrong.common.uniauth.common.util.Assert;
import com.dianrong.common.uniauth.server.synchronous.exp.FileLoadFailureException;
import com.dianrong.common.uniauth.server.synchronous.hr.bean.LoadContent;
import com.dianrong.common.uniauth.server.synchronous.support.FileLoader;
import com.dianrong.common.uniauth.server.synchronous.support.SFTPConnectionManager;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模糊匹配FTP文件的名称的加载器.
 */

@Slf4j
public class FuzzyMatchSFtpFileNameLoader implements FileLoader {

  private SFTPConnectionManager sftpConnectionManager;

  /**
   * 是否忽略大小写.
   */
  private boolean ignoreCase = true;

  public FuzzyMatchSFtpFileNameLoader(SFTPConnectionManager sftpConnectionManager) {
    Assert.notNull(sftpConnectionManager);
    this.sftpConnectionManager = sftpConnectionManager;
  }


  /**
   * 加载获取文件的输入流.
   *
   * @param file 需要再加的文件的模糊名称.
   * @throws FileLoadFailureException 加载异常.
   */
  @Override
  public LoadContent<InputStream> loadFile(String file) throws FileLoadFailureException {
    Assert.notNull(file);
    ChannelSftp channelSftp = null;
    try {
      channelSftp = this.sftpConnectionManager.getSftpClient();
      String fileName = computeFileName(channelSftp, file);
      if (fileName == null) {
        throw new FileLoadFailureException("File name start with " + file + " is not exist!");
      }
      return new LoadContent<InputStream>(channelSftp.get(fileName), fileName);
    } catch (SftpException e) {
      log.error("Failed to load file " + file + " from sftp server", e);
    } finally {
      this.sftpConnectionManager.returnSftpClient(channelSftp);
    }
    throw new FileLoadFailureException(file + " load failed");
  }

  /**
   * 加载获取文件的输入内容.
   *
   * @param file 需要再加的文件的模糊名称.
   * @throws FileLoadFailureException 加载异常.
   */
  @Override
  public LoadContent<String> loadFileContent(String file)
      throws FileLoadFailureException {
    ChannelSftp channelSftp = null;
    ByteArrayOutputStream baos = null;
    try {
      channelSftp = this.sftpConnectionManager.getSftpClient();
      String fileName = computeFileName(channelSftp, file);
      if (fileName == null) {
        throw new FileLoadFailureException("File name start with " + file + " is not exist!");
      }
      baos = new ByteArrayOutputStream();
      channelSftp.get(fileName, baos);
      return new LoadContent<String>(baos.toString("UTF-8"), fileName);
    } catch (SftpException | UnsupportedEncodingException e) {
      log.error("Failed to load file " + file + " from sftp server", e);
    } finally {
      try {
        this.sftpConnectionManager.returnSftpClient(channelSftp);
        if (baos != null) {
          baos.flush();
          baos.close();
        }
      } catch (IOException ioe) {
        log.warn("Failed to disconnect from SFTP server!", ioe);
      }
    }
    throw new FileLoadFailureException(file + " load failed");
  }

  /**
   * 计算需要load的文件名.
   *
   * @param channelSftp 外部传入的sftp服务器连接,不能为空.
   * @param file 文件的模糊名称.
   * @return 一个在FTP服务器上存在的文件的名称. 如果找不到,则返回Null.
   */
  private String computeFileName(ChannelSftp channelSftp, final String file) {
    final SingleValueHolder<List<String>> holder =
        new SingleValueHolder<>(new ArrayList<String>(1));
    try {
      channelSftp.ls(".", new ChannelSftp.LsEntrySelector() {
        @Override
        public int select(ChannelSftp.LsEntry entry) {
          String fileName = entry.getFilename();
          if (fileName != null) {
            String fileNamePrefix = file.trim();
            if (ignoreCase) {
              fileName = fileName.toLowerCase();
              fileNamePrefix = fileNamePrefix.toLowerCase();
            }
            if (fileName.startsWith(fileNamePrefix)) {
              holder.value.add(entry.getFilename());
            }
          }
          return ChannelSftp.LsEntrySelector.CONTINUE;
        }
      });
    } catch (SftpException e) {
      log.error("Failed ls", e);
    }
    List<String> value = holder.value;
    if (value.isEmpty()) {
      return null;
    }

    if (value.size() > 1) {
      // 选取更近的数据
      Collections.sort(value, new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
          return o2.compareTo(o1);
        }
      });
    }
    return holder.value.get(0);
  }

  /**
   * 辅助类.临时存放一下对象.
   */
  private static final class SingleValueHolder<T> {

    private T value;

    public SingleValueHolder() {
    }

    public <E extends T> SingleValueHolder(E value) {
      this.value = value;
    }
  }

  public SFTPConnectionManager getSftpConnectionManager() {
    return sftpConnectionManager;
  }

  public void setSftpConnectionManager(SFTPConnectionManager sftpConnectionManager) {
    Assert.notNull(sftpConnectionManager);
    this.sftpConnectionManager = sftpConnectionManager;
  }

  public boolean isIgnoreCase() {
    return ignoreCase;
  }

  public void setIgnoreCase(boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
  }
}
