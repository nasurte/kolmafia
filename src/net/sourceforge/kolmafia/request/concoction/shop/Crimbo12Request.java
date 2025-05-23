package net.sourceforge.kolmafia.request.concoction.shop;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.objectpool.Concoction;
import net.sourceforge.kolmafia.persistence.ConcoctionDatabase;
import net.sourceforge.kolmafia.request.concoction.CreateItemRequest;
import net.sourceforge.kolmafia.session.ResultProcessor;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class Crimbo12Request extends CreateItemRequest {
  private static final Pattern CREATE_PATTERN =
      Pattern.compile("shop.php.*whichshop=crimbo12.*whichitem=(\\d+).*quantity=(\\d+)");

  public Crimbo12Request(final Concoction conc) {
    super("shop.php", conc);

    this.addFormField("whichshop", "crimbo12");
    this.addFormField("action", "buyitem");
    this.addFormField("whichitem", String.valueOf(this.getItemId()));
  }

  @Override
  public void run() {
    // Attempting to make the ingredients will pull the
    // needed items from the closet if they are missing.
    // In this case, it will also create the needed white
    // pixels if they are not currently available.

    if (!this.makeIngredients()) {
      return;
    }

    KoLmafia.updateDisplay("Creating " + this.getQuantityNeeded() + " " + this.getName() + "...");
    this.addFormField("quantity", String.valueOf(this.getQuantityNeeded()));
    super.run();
  }

  public static final boolean registerRequest(final String urlString) {
    Matcher createMatcher = Crimbo12Request.CREATE_PATTERN.matcher(urlString);
    if (!createMatcher.find()) {
      return false;
    }

    // Item ID of the base item
    int itemId = StringUtilities.parseInt(createMatcher.group(1));
    int quantity = StringUtilities.parseInt(createMatcher.group(2));

    AdventureResult[] ingredients = ConcoctionDatabase.getIngredients(itemId);
    StringBuffer text = new StringBuffer();
    text.append("Combine ");

    for (int i = 0; i < ingredients.length; ++i) {
      if (i > 0) {
        text.append(" + ");
      }

      text.append(ingredients[i].getCount() * quantity);
      text.append(" ");
      text.append(ingredients[i].getName());
      ResultProcessor.processResult(
          ingredients[i].getInstance(-1 * ingredients[i].getCount() * quantity));
    }

    RequestLogger.updateSessionLog();
    RequestLogger.updateSessionLog(text.toString());

    return true;
  }
}
