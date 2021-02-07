package test;

/**
 * This Exception is thrown if the grammar has some errors. See message for
 * details.
 *
 * @author Thomas Thuem
 */
public class UnsupportedModelException extends Exception {

	private static final long serialVersionUID = -4060297146846881685L;

	public final int lineNumber;

	public UnsupportedModelException(String message, int lineNumber) {
		super(message);
		this.lineNumber = lineNumber;
	}

}
