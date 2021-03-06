package games.strategy.net.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.java.Log;

/**
 * A packet of data to be written over the network.
 *
 * <p>Packets do not correspond to ip packets. A packet is just the data for one serialized object.
 *
 * <p>The packet is written over the network as 32 bits indicating the size in bytes, then the data
 * itself.
 */
@Log
class SocketWriteData {
  private static final AtomicInteger counter = new AtomicInteger();
  private final ByteBuffer size;
  private final ByteBuffer content;
  private final int number = counter.incrementAndGet();
  // how many times we called write before we finished writing ourselves
  private int writeCalls = 0;

  SocketWriteData(final byte[] data, final int count) {
    content = ByteBuffer.allocate(count);
    content.put(data, 0, count);
    size = ByteBuffer.allocate(4);
    if (count < 0 || count > SocketReadData.MAX_MESSAGE_SIZE) {
      throw new IllegalStateException("Invalid message size:" + count);
    }
    size.putInt(count ^ SocketReadData.MAGIC);
    size.flip();
    content.flip();
  }

  int size() {
    return size.capacity() + content.capacity();
  }

  int getWriteCalls() {
    return writeCalls;
  }

  /**
   * Writes any pending data to the specified channel.
   *
   * @return true if the write has written the entire message.
   */
  boolean write(final SocketChannel channel) throws IOException {
    writeCalls++;
    if (size.hasRemaining()) {
      final int count = channel.write(size);
      if (count == -1) {
        throw new IOException("triplea: end of stream detected");
      }
      log.finest(() -> "wrote size_buffer bytes:" + count);
      // we could not write everything
      if (size.hasRemaining()) {
        return false;
      }
    }
    final int count = channel.write(content);
    if (count == -1) {
      throw new IOException("triplea: end of stream detected");
    }
    log.finest(() -> "wrote content bytes:" + count);
    return !content.hasRemaining();
  }

  @Override
  public String toString() {
    return "<id:" + number + " size:" + content.capacity() + ">";
  }
}
