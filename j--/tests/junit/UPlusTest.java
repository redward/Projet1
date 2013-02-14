// Authors : Creciun Eduard, Lambert Andrew

package junit;

import junit.framework.TestCase;
import pass.UPlus;

public class UPlusTest extends TestCase
{
	private UPlus uplus;
	
	protected void setUp() throws Exception
	{
		super.setUp();
		uplus = new UPlus();
	}
	
	protected void tearDown() throws Exception
	{
		super.tearDown();
	}
	
	public void testUPlus(){
		this.assertEquals(uplus.uPlus(0), 0);
		this.assertEquals(uplus.uPlus(42), 42);
		this.assertEquals(uplus.uPlus(-1), -1);
	}
}