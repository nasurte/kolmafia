/**
 * Copyright (c) 2005, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.math.BigInteger;

import net.java.dev.spellcast.utilities.UtilityConstants;
import net.java.dev.spellcast.utilities.LockableListModel;
import net.java.dev.spellcast.utilities.SortedListModel;

/**
 * The main class for the <code>KoLmafia</code> package.  This
 * class encapsulates most of the data relevant to any given
 * session of <code>Kingdom of Loathing</code> and currently
 * functions as the blackboard in the architecture.  When data
 * listeners are implemented, it will continue to manage most
 * of the interactions.
 */

public abstract class KoLmafia implements KoLConstants, UtilityConstants
{
	protected static final String [] hermitItemNames = { "ten-leaf clover", "wooden figurine", "hot buttered roll", "banjo strings",
		"jaba�ero pepper", "fortune cookie", "golden twig", "ketchup", "catsup", "sweet rims", "dingy planks", "volleyball" };
	protected static final int [] hermitItemNumbers = { 24, 46, 47, 52, 55, 61, 66, 106, 107, 135, 140, 527 };

	protected boolean isLoggingIn;
	protected String password, sessionID, passwordHash;
	protected KoLCharacter characterData;
	protected KoLMessenger loathingChat;
	protected KoLMailManager loathingMail;

	protected KoLSettings settings;
	protected PrintStream logStream;
	protected boolean permitContinue;

	protected int [] initialStats;
	protected int [] currentStats;
	protected int [] fullStatGain;

	protected SortedListModel saveStateNames;
	protected List recentEffects;
	protected SortedListModel tally;
	protected LockableListModel inventory, closet, usableItems;

	/**
	 * The main method.  Currently, it instantiates a single instance
	 * of the <code>KoLmafiaGUI</code>.
	 */

	public static void main( String [] args )
	{
		boolean useGUI = true;
		for ( int i = 0; i < args.length; ++i )
		{
			if ( args[i].equals( "--CLI" ) )
				useGUI = false;
			if ( args[i].equals( "--GUI" ) )
				useGUI = true;
		}

		if ( useGUI )
			KoLmafiaGUI.main( args );
		else
			KoLmafiaCLI.main( args );
	}

	/**
	 * Constructs a new <code>KoLmafia</code> object.  All data fields
	 * are initialized to their default values, the global settings
	 * are loaded from disk.
	 */

	public KoLmafia()
	{
		this.isLoggingIn = true;

		this.initialStats = new int[3];
		this.currentStats = new int[3];
		this.fullStatGain = new int[3];

		this.settings = new KoLSettings();
		this.saveStateNames = new SortedListModel();
		String saveStateSettings = settings.getProperty( "saveState" );
		if ( saveStateSettings != null )
		{
			String [] currentNames = saveStateSettings.split( "//" );
			for ( int i = 0; i < currentNames.length; ++i )
				saveStateNames.add( currentNames[i] );
		}

		// This line is added to clear out data from previous
		// releases of KoLmafia - the extra disk access does
		// affect performance, but not significantly.

		storeSaveStates();
		deinitialize();
	}

	/**
	 * Updates the currently active display in the <code>KoLmafia</code>
	 * session.
	 */

	public abstract void updateDisplay( int state, String message );
	public abstract void requestFocus();

	/**
	 * Initializes the <code>KoLmafia</code> session.  Called after
	 * the login has been confirmed to notify the client that the
	 * login was successful, the user-specific settings should be
	 * loaded, and the user can begin adventuring.
	 */

	public void initialize( String loginname, String sessionID, boolean getBreakfast )
	{
		// Initialize the variables to their initial
		// states to avoid null pointers getting thrown
		// all over the place

		this.sessionID = sessionID;
		this.characterData = new KoLCharacter( loginname );
		this.inventory = characterData.getInventory();
		this.usableItems = new SortedListModel();
		this.closet = characterData.getCloset();
		this.tally = new SortedListModel();
		this.recentEffects = new ArrayList();

		// Fill the tally with junk information

		processResult( new AdventureResult( AdventureResult.MEAT ) );
		processResult( new AdventureResult( AdventureResult.SUBSTATS ) );
		processResult( new AdventureResult( AdventureResult.FULLSTATS, fullStatGain ) );
		processResult( new AdventureResult( AdventureResult.DIVIDER ) );

		// Begin by loading the user-specific settings.

		logStream.println( "Loading user settings for " + loginname + "..." );
		this.settings = new KoLSettings( loginname );

		// Remove the password data; it doesn't need to be stored
		// in every single .kcs file.

		saveStateNames.clear();
		storeSaveStates();

		// Now!  Check to make sure the user didn't cancel
		// during any of the actual loading of elements

		if ( !permitContinue )
		{
			this.sessionID = null;
			this.permitContinue = true;
			return;
		}

		(new PasswordHashRequest( this )).run();

		if ( !permitContinue )
		{
			this.sessionID = null;
			this.permitContinue = true;
			return;
		}

		if ( settings.getProperty( "skipCharacterData" ) == null )
		{
			(new CharsheetRequest( this )).run();
			(new CampgroundRequest( this )).run();

			initialStats[0] = characterData.getTotalMuscle();
			initialStats[1] = characterData.getTotalMysticality();
			initialStats[2] = characterData.getTotalMoxie();

			for ( int i = 0; i < 3; ++i )
				currentStats[i] = initialStats[i];
		}

		if ( !permitContinue )
		{
			this.sessionID = null;
			this.permitContinue = true;
			return;
		}

		if ( settings.getProperty( "skipInventory" ) == null )
			(new EquipmentRequest( this )).run();

		if ( !permitContinue )
		{
			this.sessionID = null;
			this.permitContinue = true;
			return;
		}

		if ( settings.getProperty( "skipFamiliarData" ) == null )
			(new FamiliarRequest( this )).run();

		// Initially the tally to the necessary values

		processResult( new AdventureResult( AdventureResult.MEAT ) );
		processResult( new AdventureResult( AdventureResult.SUBSTATS ) );
		processResult( new AdventureResult( AdventureResult.FULLSTATS, fullStatGain ) );
		processResult( new AdventureResult( AdventureResult.DIVIDER ) );

		applyRecentEffects();

		if ( getBreakfast )
			getBreakfast();

		this.isLoggingIn = false;
		this.loathingMail = new KoLMailManager( this );
	}

	/**
	 * Utility method used to notify the client that it should attempt
	 * to retrieve breakfast.
	 */

	public void getBreakfast()
	{
		updateDisplay( DISABLED_STATE, "Retrieving breakfast..." );

		if ( characterData.hasToaster() )
			for ( int i = 0; i < 3 && permitContinue; ++i )
				(new CampgroundRequest( this, "toast" )).run();

		permitContinue = true;

		if ( characterData.hasArches() )
			(new CampgroundRequest( this, "arches" )).run();

		if ( characterData.canSummonNoodles() )
			(new UseSkillRequest( this, "Pastamastery", "", 3 )).run();
		if ( characterData.canSummonReagent() )
			(new UseSkillRequest( this, "Advanced Saucecrafting", "", 3 )).run();
	}

	/**
	 * Deinitializes the <code>KoLmafia</code> session.  Called after
	 * the user has logged out.
	 */

	public void deinitialize()
	{
		sessionID = null;
		passwordHash = null;
		permitContinue = false;

		deinitializeChat();
		deinitializeLogStream();
		this.permitContinue = true;
	}

	/**
	 * Utility method to parse an individual adventuring result.
	 * This method determines what the result actually was and
	 * adds it to the tally.
	 *
	 * @param	result	String to parse for the result
	 */

	public void parseResult( String result )
	{
		// Because of the simplified parsing, there's a chance that
		// the "gain" acquired wasn't a subpoint (in other words, it
		// includes the word "a" or "some"), which causes a NFE or
		// possibly a ParseException to be thrown.  Catch them and
		// do nothing (eventhough it's technically bad style).

		if ( result.startsWith( "You gain a" ) || result.startsWith( "You gain some" ) )
			return;

		try
		{
			logStream.println( "Parsing adventure result:\n\t" + result );
			processResult( AdventureResult.parseResult( result ) );
		}
		catch ( Exception e )
		{
			logStream.println( e );
			e.printStackTrace( logStream );
		}
	}

	/**
	 * Utility method used to add an adventure result to the
	 * tally directly.  This is used whenever the nature of the
	 * result is already known and no additional parsing is needed.
	 *
	 * @param	result	Result to add to the running tally of adventure results
	 */

	public void processResult( AdventureResult result )
	{
		String resultName = result.getName();
		if ( result.isStatusEffect() )
			recentEffects.add( result );
		else if ( result.isItem() || resultName.equals( AdventureResult.SUBSTATS ) || resultName.equals( AdventureResult.MEAT ) )
			AdventureResult.addResultToList( tally, result );

		if ( result.isItem() && TradeableItemDatabase.contains( resultName ) && TradeableItemDatabase.isUsable( resultName ) )
			AdventureResult.addResultToList( usableItems, result );

		characterData.processResult( result );

		// Now, if it's an actual stat gain, be sure to update the
		// list to reflect the current value of stats so far.

		if ( resultName.equals( AdventureResult.SUBSTATS ) )
		{
			for ( int i = 0; i < 3; ++i )
				fullStatGain[i] = KoLCharacter.calculateBasePoints( currentStats[i] ) -
					KoLCharacter.calculateBasePoints( initialStats[i] );

			if ( tally.size() > 7 )
				tally.set( 7, new AdventureResult( AdventureResult.FULLSTATS, fullStatGain ) );
		}
	}

	/**
	 * Adds the recent effects accumulated so far to the actual effects.
	 * This should be called after the previous effects were decremented,
	 * if adventuring took place.
	 */

	public void applyRecentEffects()
	{
		for ( int j = 0; j < recentEffects.size(); ++j )
			AdventureResult.addResultToList( characterData.getEffects(), (AdventureResult) recentEffects.get(j) );
		recentEffects.clear();
	}

	/**
	 * Retrieves the character data for the character associated with the current
	 * gaming session.
	 *
	 * @return	The character data for this character
	 */

	public KoLCharacter getCharacterData()
	{	return characterData;
	}

	/**
	 * Retrieves the user ID for the character of the current gaming session.
	 * @return	The user ID of the current user
	 */

	public int getUserID()
	{	return characterData.getUserID();
	}

	/**
	 * Retrieves the login name for this <code>KoLmafia</code> session.
	 * @return	The login name of the current user
	 */

	public String getLoginName()
	{	return (characterData == null) ? null : characterData.getUsername();
	}

	/**
	 * Retrieves the session ID for this <code>KoLmafia</code> session.
	 * @return	The session ID of the current session
	 */

	public String getSessionID()
	{	return sessionID;
	}

	/**
	 * Stores the password hash for this <code>KoLmafia</code> session.
	 * @param	passwordHash	The password hash for this session
	 */

	public void setPasswordHash( String passwordHash )
	{	this.passwordHash = passwordHash;
	}

	/**
	 * Retrieves the password hash for this <code>KoLmafia</code> session.
	 * @return	The password hash of the current session
	 */

	public String getPasswordHash()
	{	return passwordHash;
	}

	/**
	 * Retrieves the character's inventory.
	 * @return	The character's inventory
	 */

	public LockableListModel getInventory()
	{	return inventory;
	}

	/**
	 * Retrieves the character's closet.
	 * @return	The character's closet
	 */

	public LockableListModel getCloset()
	{	return closet;
	}

	/**
	 * Retrieves the usable items in the character's inventory
	 * @return	The character's usable items
	 */

	public LockableListModel getUsableItems()
	{	return usableItems;
	}

	/**
	 * Returns whether or not the current user has a ten-leaf clover.
	 * Because inventory management is not yet implemented, this
	 * method always returns true.
	 *
	 * @return	<code>true</code>
	 */

	public boolean isLuckyCharacter()
	{	return inventory != null && inventory.contains( new AdventureResult( "ten-leaf clover", 0 ) );
	}

	/**
	 * Utility method called inbetween battles.  This method
	 * checks to see if the character's HP has dropped below
	 * the tolerance value, and autorecovers if it has (if
	 * the user has specified this in their settings).
	 */

	private void autoRecoverHP()
	{
		String autoRecoverSettings = settings.getProperty( "hpAutoRecover" );
		String recoveryScriptSettings = settings.getProperty( "hpRecoveryScript" );

		if ( autoRecoverSettings == null )
			return;
		double autoRecover = Double.parseDouble( autoRecoverSettings ) * (double) characterData.getMaximumHP();

		if ( (double) characterData.getCurrentHP() <= autoRecover && recoveryScriptSettings != null && recoveryScriptSettings.length() > 0 )
		{
			permitContinue = true;
			updateDisplay( DISABLED_STATE, "Executing HP auto-recovery script..." );
			try
			{
				(new KoLmafiaCLI( this, recoveryScriptSettings )).listenForCommands();

				if ( permitContinue )
				{
					permitContinue = characterData.getCurrentHP() >= autoRecover;
					if ( !permitContinue )
						updateDisplay( ERROR_STATE, "Insufficient HP to continue" );
				}
			}
			catch ( Exception e )
			{
				updateDisplay( ERROR_STATE, "Could not find HP auto-recovery script." );
				permitContinue = false;
				return;
			}
		}
	}

	/**
	 * Utility method called inbetween commands.  This method
	 * checks to see if the character's MP has dropped below
	 * the tolerance value, and autorecovers if it has (if
	 * the user has specified this in their settings).
	 */

	private void autoRecoverMP()
	{
		String autoRecoverSettings = settings.getProperty( "mpAutoRecover" );
		String recoveryScriptSettings = settings.getProperty( "mpRecoveryScript" );

		if ( autoRecoverSettings == null )
			return;
		double autoRecover = Double.parseDouble( autoRecoverSettings ) * (double) characterData.getMaximumMP();

		if ( (double) characterData.getCurrentMP() <= autoRecover && recoveryScriptSettings != null && recoveryScriptSettings.length() > 0 )
		{
			permitContinue = true;
			updateDisplay( DISABLED_STATE, "Executing MP auto-recovery script..." );
			try
			{
				(new KoLmafiaCLI( this, recoveryScriptSettings )).listenForCommands();

				if ( permitContinue )
				{
					permitContinue = characterData.getCurrentMP() >= autoRecover;
					if ( !permitContinue )
						updateDisplay( ERROR_STATE, "Insufficient MP to continue" );
				}
			}
			catch ( Exception e )
			{
				updateDisplay( ERROR_STATE, "Could not find MP auto-recovery script." );
				permitContinue = false;
				return;
			}
		}
	}

	/**
	 * Makes the given request for the given number of iterations,
	 * or until continues are no longer possible, either through
	 * user cancellation or something occuring which prevents the
	 * requests from resuming.  Overriding classes should make use
	 * of the <code>permitContinue</code> variable to determine
	 * if one of the requests has suggested that the user stop.
	 *
	 * @param	request	The request made by the user
	 * @param	iterations	The number of times the request should be repeated
	 */

	public void makeRequest( Runnable request, int iterations )
	{
		try
		{
			this.permitContinue = true;
			boolean pulledOver = false;
			int iterationsRemaining = iterations;

			if ( request.toString().equals( "The Hermitage" ) )
				makeHermitRequest( iterations );
			else if ( request.toString().startsWith( "Gym" ) )
				(new ClanGymRequest( this, Integer.parseInt( ((KoLAdventure)request).getAdventureID() ), iterations )).run();
			else
			{
				if ( request instanceof KoLAdventure && request.toString().indexOf( "Campground" ) == -1 && characterData.getInebriety() > 19 )
				{
					permitContinue = confirmDrunkenRequest();
					pulledOver = true;
				}

				if ( request instanceof KoLAdventure && !request.toString().startsWith( "Campsite" ) )
					autoRecoverHP();

				if ( (request instanceof KoLAdventure && !request.toString().startsWith( "Campsite" )) || request instanceof UseSkillRequest )
					autoRecoverMP();

				for ( int i = 1; permitContinue && iterationsRemaining > 0; ++i )
				{
					updateDisplay( DISABLED_STATE, "Request " + i + " in progress..." );
					request.run();
					applyRecentEffects();

					// Make sure you only decrement iterations if the
					// continue was permitted.  This resolves the issue
					// of incorrectly updating the client if something
					// occurred on the last iteration.

					if ( permitContinue )
					{
						--iterationsRemaining;
						autoRecoverHP();
						autoRecoverMP();

						if ( request instanceof KoLAdventure && characterData.getInebriety() > 19 && !pulledOver )
						{
							permitContinue = confirmDrunkenRequest();
							pulledOver = true;
						}
					}
					else if ( (request instanceof KoLAdventure && !request.toString().startsWith( "Campsite" )) || request instanceof UseSkillRequest )
					{
						autoRecoverHP();
						autoRecoverMP();
						if ( permitContinue )
						{
							if ( characterData.getInebriety() > 19 && !pulledOver )
							{
								permitContinue = confirmDrunkenRequest();
								pulledOver = true;
							}

							if ( permitContinue )
								--iterationsRemaining;
						}
					}
				}

				if ( permitContinue && iterationsRemaining <= 0 && !(request instanceof UseSkillRequest) )
					updateDisplay( ENABLED_STATE, "Requests completed!" );
			}
		}
		catch ( RuntimeException e )
		{
			// In the event that an exception occurs during the
			// request processing, catch it here, print it to
			// the logger (whatever it may be), and notify the
			// user that an error was encountered.

			logStream.println( e );
			e.printStackTrace( logStream );
			updateDisplay( ERROR_STATE, "Unexpected error." );
		}

	}

	/**
	 * Makes a request to the hermit, looking for the given number of
	 * items.  This method should prompt the user to determine which
	 * item to retrieve the hermit, if no default has been specified
	 * in the user settings.
	 *
	 * @param	tradeCount	The number of items to request
	 */

	protected abstract void makeHermitRequest( int tradeCount );

	/**
	 * Confirms whether or not the user wants to make a drunken
	 * request.  This should be called before doing requests when
	 * the user is in an inebrieted state.
	 *
	 * @return	<code>true</code> if the user wishes to adventure drunk
	 */

	protected abstract boolean confirmDrunkenRequest();

	/**
	 * For requests that do not use the client's "makeRequest()"
	 * method, this method is used to reset the continue state.
	 */

	public void resetContinueState()
	{	this.permitContinue = true;
	}

	/**
	 * Cancels the user's current request.  Note that if there are
	 * no requests running, this method does nothing.
	 */

	public void cancelRequest()
	{	this.permitContinue = false;
	}

	/**
	 * Retrieves whether or not continuation of an adventure or request
	 * is permitted by the client, or by current circumstances in-game.
	 *
	 * @return	<code>true</code> if requests are allowed to continue
	 */

	public boolean permitsContinue()
	{	return permitContinue;
	}

	/**
	 * Initializes a stream for logging debugging information.  This
	 * method creates a <code>KoLmafia.log</code> file in the default
	 * data directory if one does not exist, or appends to the existing
	 * log.  This method should only be invoked if the user wishes to
	 * assist in beta testing because the output is VERY verbose.
	 */

	public void initializeLogStream()
	{
		// First, ensure that a log stream has not already been
		// initialized - this can be checked by observing what
		// class the current log stream is.

		if ( !(logStream instanceof NullStream) )
			return;

		try
		{
			File f = new File( DATA_DIRECTORY + "KoLmafia.log" );

			if ( !f.exists() )
				f.createNewFile();

			logStream = new LogStream( f );
		}
		catch ( IOException e )
		{
			// This should not happen, unless the user
			// security settings are too high to allow
			// programs to write output; therefore,
			// pretend for now that everything works.
		}
	}

	/**
	 * De-initializes the log stream.  This method should only
	 * be called when the user wishes to stop logging the session.
	 */

	public void deinitializeLogStream()
	{
		if ( logStream != null )
			logStream.close();
		logStream = new NullStream();
	}

	/**
	 * Retrieves the current settings for the current session.  Note
	 * that if this is invoked before initialization, this method
	 * will return the global settings.
	 *
	 * @return	The settings for the current session
	 */

	public KoLSettings getSettings()
	{	return settings;
	}

	/**
	 * Retrieves the stream currently used for logging debug output.
	 * @return	The stream used for debug output
	 */

	public PrintStream getLogStream()
	{	return logStream;
	}

	/**
	 * Returns the messaging client associated with the current
	 * session of KoLmafia.
	 *
	 * @return	The messaging client for the current session
	 */

	public KoLMessenger getMessenger()
	{	return loathingChat;
	}

	/**
	 * Returns the mailing client associated with the current
	 * session of KoLmafia.
	 *
	 * @return	The mailing client for the current session
	 */

	public KoLMailManager getMailManager()
	{	return loathingMail;
	}

	/**
	 * Initializes the chat buffer with the provided chat pane.
	 * Note that the chat refresher will also be initialized
	 * by calling this method; to stop the chat refresher, call
	 * the <code>deinitializeChat()</code> method.
	 */

	public void initializeChat()
	{
		loathingChat = new KoLMessenger( this );
		loathingChat.initialize();
	}

	/**
	 * De-initializes the chat.  This closes any existing logging
	 * activity occurring within the chat and disables future
	 * chat refresher requests.  In order to re-initialize the
	 * chat, please call the <code>initializeChat()</code> method.
	 */

	public void deinitializeChat()
	{
		if ( loathingChat != null )
		{
			loathingChat.dispose();
			loathingChat = null;
		}
	}

	/**
	 * Returns whether or not the client is current in a login state.
	 * While the client is in a login state, only login-related
	 * activities should be permitted.
	 */

	public boolean inLoginState()
	{	return isLoggingIn;
	}

	/**
	 * Utility method used to decode a saved password.
	 * This should be called whenever a new password
	 * intends to be stored in the global file.
	 */

	public void addSaveState( String loginname, String password )
	{
		try
		{
			if ( !saveStateNames.contains( loginname ) )
				saveStateNames.add( loginname );

			storeSaveStates();
			String encodedString = URLEncoder.encode( password, "UTF-8" ).replaceAll( "\\-", "%2D" ).replaceAll(
				"\\.", "%2E" ).replaceAll( "\\*", "%2A" ).replaceAll( "_", "%5F" ).replaceAll( "\\+", "%20" );

			// Handle capital letters

			StringBuffer encodedCaseSensitiveString = new StringBuffer();
			char currentCharacter;
			for ( int i = 0; i < encodedString.length(); ++i )
			{
				currentCharacter = encodedString.charAt(i);
				if ( currentCharacter >= 'A' && currentCharacter <= 'Z' )
				{
					encodedCaseSensitiveString.append( '%' );
					encodedCaseSensitiveString.append( Integer.toHexString( (int) currentCharacter ) );
				}
				else
					encodedCaseSensitiveString.append( currentCharacter );
			}

			String [] encodedParts = encodedCaseSensitiveString.toString().split( "%" );

			// Complete the encoding process

			StringBuffer saveState = new StringBuffer();
			if ( encodedParts[0].length() != 0 )
				saveState.append( (new BigInteger( encodedParts[0], 36 )).toString( 10 ) );

			for ( int i = 1; i < encodedParts.length; ++i )
			{
				saveState.append( ' ' );
				saveState.append( (new BigInteger( encodedParts[i], 36 )).toString( 10 ) );
			}

			settings.setProperty( "saveState." + loginname, saveState.toString() );
			settings.saveSettings();
		}
		catch ( java.io.UnsupportedEncodingException e )
		{
			// UTF-8 is a very generic encoding scheme; this
			// exception should never be thrown.  But if it
			// is, just ignore it for now.  Better exception
			// handling when it becomes necessary.
		}
	}

	public void removeSaveState( String loginname )
	{
		if ( loginname == null )
			return;

		Object [] saveStates = settings.keySet().toArray();
		for ( int i = 0; i < saveStates.length; ++i )
		{
			if ( loginname.equalsIgnoreCase( (String) saveStates[i] ) )
			{
				saveStateNames.remove( loginname );
				storeSaveStates();
				return;
			}
		}
	}

	private void storeSaveStates()
	{
		StringBuffer saveStateBuffer = new StringBuffer();
		Iterator saveStateIterator = saveStateNames.iterator();

		if ( saveStateIterator.hasNext() )
		{
			saveStateBuffer.append( saveStateIterator.next() );
			while ( saveStateIterator.hasNext() )
			{
				saveStateBuffer.append( "//" );
				saveStateBuffer.append( saveStateIterator.next() );
			}
			settings.setProperty( "saveState", saveStateBuffer.toString() );
		}
		else
			settings.remove( "saveState" );

		// Now, removing any passwords that were stored
		// which are no longer in the save state list

		String currentKey;
		Object [] settingsArray = settings.keySet().toArray();

		for ( int i = 0; i < settingsArray.length; ++i )
		{
			currentKey = (String) settingsArray[i];
			if ( currentKey.startsWith( "saveState." ) && !saveStateNames.contains( currentKey.substring( 10 ) ) )
				settings.remove( currentKey );
		}

		settings.saveSettings();
	}

	/**
	 * Utility method used to decode a saved password.
	 * This should be called whenever a new password
	 * intends to be stored in the global file.
	 */

	public String getSaveState( String loginname )
	{
		try
		{
			Object [] settingKeys = settings.keySet().toArray();
			String password = null;
			String ignoreCaseKey = "saveState." + loginname;
			String currentKey;

			for ( int i = 0; i < settingKeys.length && password == null; ++i )
			{
				currentKey = (String) settingKeys[i];
				if ( currentKey.equalsIgnoreCase( ignoreCaseKey ) )
				{
					password = settings.getProperty( currentKey );
					settings.remove( currentKey );
					settings.setProperty( ignoreCaseKey, password );
					settings.saveSettings();
				}
			}

			if ( password == null )
				return null;

			String [] decodedParts = password.split( " " );
			StringBuffer saveState = new StringBuffer();

			if ( decodedParts[0].length() != 0 )
				saveState.append( (new BigInteger( decodedParts[0], 10 )).toString( 36 ) );

			for ( int i = 1; i < decodedParts.length; ++i )
			{
				saveState.append( '%' );
				saveState.append( (new BigInteger( decodedParts[i], 10 )).toString( 36 ) );
			}

			return URLDecoder.decode( saveState.toString().replaceAll( "%20", "+" ).replaceAll( "%5F", "_" ).replaceAll(
				"%2A", "*" ).replaceAll( "%2E", "." ).replaceAll( "%2D", "-" ), "UTF-8" );
		}
		catch ( java.io.UnsupportedEncodingException e )
		{
			// UTF-8 is a very generic encoding scheme; this
			// exception should never be thrown.  But if it
			// is, just ignore it for now.  Better exception
			// handling when it becomes necessary.

			return null;
		}
	}
}
