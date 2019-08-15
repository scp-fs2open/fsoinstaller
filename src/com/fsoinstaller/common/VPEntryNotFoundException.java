
import java.io.IOException;

/**
 *
 * @author Ryan
 */
public class VPEntryNotFoundException extends IOException {

    /**
     * Creates a new instance of <code>VPEntryNotFoundException</code> without
     * detail message.
     */
    public VPEntryNotFoundException() {
    }

    /**
     * Constructs an instance of <code>VPEntryNotFoundException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public VPEntryNotFoundException(String msg) {
        super(msg);
    }
}
