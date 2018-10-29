package com.dlsc.preferencesfx.model;

import static com.dlsc.preferencesfx.util.Constants.DEFAULT_CATEGORY;
import static com.dlsc.preferencesfx.util.Constants.DEFAULT_DIVIDER_POSITION;

import com.dlsc.formsfx.model.util.TranslationService;
import com.dlsc.preferencesfx.PreferencesFxEvent;
import com.dlsc.preferencesfx.history.History;
import com.dlsc.preferencesfx.util.PreferencesFxUtils;
import com.dlsc.preferencesfx.util.SearchHandler;
import com.dlsc.preferencesfx.util.StorageHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the model which holds all of the data and logic which is not limited to presenters.
 *
 * @author François Martin
 * @author Marco Sanfratello
 */
public class PreferencesFxModel {
  private static final Logger LOGGER =
      LogManager.getLogger(PreferencesFxModel.class.getName());

  private ObjectProperty<Category> displayedCategory = new SimpleObjectProperty<>();

  private StringProperty searchText = new SimpleStringProperty();

  private List<Category> categories;
  private List<Category> categoriesFlat;
  private StorageHandler storageHandler;
  private SearchHandler searchHandler;
  private History history;
  private ObjectProperty<TranslationService> translationService = new SimpleObjectProperty<>();

  private boolean persistWindowState = false;
  private boolean saveSettings = true;
  private boolean historyDebugState = false;
  private boolean isOneCategoryLayout;
  private BooleanProperty buttonsVisible = new SimpleBooleanProperty(true);
  private DoubleProperty dividerPosition = new SimpleDoubleProperty(DEFAULT_DIVIDER_POSITION);

  private final Map<EventType<PreferencesFxEvent>, List<EventHandler<? super PreferencesFxEvent>>> eventHandlers = new ConcurrentHashMap<>();

  /**
   * Initializes a new model.
   *
   * @param storageHandler the {@link StorageHandler} to use for saving and loading
   * @param searchHandler  the {@link SearchHandler} to use for handling the searches
   * @param history        the {@link History} in which to save the changes and handle undo / redo
   * @param categories     the categories to be displayed, along with the groups and settings
   */
  public PreferencesFxModel(
      StorageHandler storageHandler,
      SearchHandler searchHandler,
      History history,
      Category[] categories
  ) {
    this.storageHandler = storageHandler;
    this.searchHandler = searchHandler;
    this.history = history;
    this.categories = Arrays.asList(categories);
    isOneCategoryLayout = categories.length == 1;
    categoriesFlat = PreferencesFxUtils.flattenCategories(this.categories);
    initializeCategoryTranslation();
    setDisplayedCategory(getCategories().get(DEFAULT_CATEGORY));
    createBreadcrumbs(this.categories);
  }

  /**
   * Sets up a binding of the TranslationService on the model, so that the Category's title gets
   * translated properly according to the TranslationService used.
   */
  private void initializeCategoryTranslation() {
    categoriesFlat.forEach(category -> translationServiceProperty().addListener((observable, oldValue, newValue) -> {
	  category.translate(newValue);
	  // listen for i18n changes in the TranslationService for this Category
	  newValue.addListener(() -> category.translate(newValue));
	}));
  }

  private void createBreadcrumbs(List<Category> categories) {
    categories.forEach(category -> {
      if (category.getGroups() != null) {
        category.getGroups().forEach(group -> group.addToBreadcrumb(category.getBreadcrumb()));
      }
      if (category.getChildren() != null) {
        category.createBreadcrumbs();
      }
    });
  }

  public List<Category> getCategories() {
    return categories;
  }

  public boolean isPersistWindowState() {
    return persistWindowState;
  }

  public void setPersistWindowState(boolean persistWindowState) {
    this.persistWindowState = persistWindowState;
  }

  public boolean isSaveSettings() {
    return saveSettings;
  }

  public void setSaveSettings(boolean saveSettings) {
    this.saveSettings = saveSettings;
  }

  public History getHistory() {
    return history;
  }

  public StorageHandler getStorageHandler() {
    return storageHandler;
  }

  public boolean getHistoryDebugState() {
    return historyDebugState;
  }

  public void setHistoryDebugState(boolean historyDebugState) {
    this.historyDebugState = historyDebugState;
  }


  // ------ StorageHandler work -------------

  /**
   * Saves the current selected Category.
   */
  public void saveSelectedCategory() {
    storageHandler.saveSelectedCategory(displayedCategory.get().getBreadcrumb());
  }

  /**
   * Loads the last selected Category before exiting the Preferences window.
   *
   * @return last selected Category
   */
  public Category loadSelectedCategory() {
    String breadcrumb = storageHandler.loadSelectedCategory();
    Category defaultCategory = getCategories().get(DEFAULT_CATEGORY);
    if (breadcrumb == null) {
      return defaultCategory;
    }
    return categoriesFlat.stream()
        .filter(category -> category.getBreadcrumb().equals(breadcrumb))
        .findAny().orElse(defaultCategory);
  }

  /**
   * Saves all of the values of the settings using a {@link StorageHandler}.
   */
  private void saveSettingValues() {
    PreferencesFxUtils.categoriesToSettings(
        getCategoriesFlat()
    ).forEach(setting -> setting.saveSettingValue(storageHandler));
  }

  /**
   * Load all of the values of the settings using a {@link StorageHandler} and attaches a
   * listener for {@link History}, so that it will be notified of changes to the setting's values.
   */
  public void loadSettingValues() {
    PreferencesFxUtils.categoriesToSettings(categoriesFlat)
        .forEach(setting -> {
          LOGGER.trace("Loading: " + setting.getBreadcrumb());
          if (saveSettings) {
            setting.loadSettingValue(storageHandler);
          }
          history.attachChangeListener(setting);
        });
  }

  public Category getDisplayedCategory() {
    return displayedCategory.get();
  }

  public void setDisplayedCategory(Category displayedCategory) {
    LOGGER.trace("Change displayed category to: " + displayedCategory);
    this.displayedCategory.set(displayedCategory);
  }

  public ReadOnlyObjectProperty<Category> displayedCategoryProperty() {
    return displayedCategory;
  }

  public String getSearchText() {
    return searchText.get();
  }

  public void setSearchText(String searchText) {
    this.searchText.set(searchText);
  }

  public StringProperty searchTextProperty() {
    return searchText;
  }

  public List<Category> getCategoriesFlat() {
    return categoriesFlat;
  }

  public SearchHandler getSearchHandler() {
    return searchHandler;
  }

  public boolean getButtonsVisible() {
    return buttonsVisible.get();
  }

  public BooleanProperty buttonsVisibleProperty() {
    return buttonsVisible;
  }

  public void setButtonsVisible(boolean buttonsVisible) {
    this.buttonsVisible.set(buttonsVisible);
  }

  public TranslationService getTranslationService() {
    return translationService.get();
  }

  public ObjectProperty<TranslationService> translationServiceProperty() {
    return translationService;
  }

  public void setTranslationService(TranslationService translationService) {
    this.translationService.set(translationService);
  }

  public double getDividerPosition() {
    return dividerPosition.get();
  }

  public DoubleProperty dividerPositionProperty() {
    return dividerPosition;
  }

  public void setDividerPosition(double dividerPosition) {
    this.dividerPosition.set(dividerPosition);
  }

  public boolean isOneCategoryLayout() {
    return isOneCategoryLayout;
  }

  public void addEventHandler(EventType<PreferencesFxEvent> eventType, EventHandler<? super PreferencesFxEvent> eventHandler) {
    if (eventType == null) {
      throw new NullPointerException("Argument eventType must not be null");
    }
    if (eventHandler == null) {
      throw new NullPointerException("Argument eventHandler must not be null");
    }

    this.eventHandlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(eventHandler);
  }

  public void removeEventHandler(EventType<PreferencesFxEvent> eventType, EventHandler<? super PreferencesFxEvent> eventHandler) {
    if (eventType == null) {
      throw new NullPointerException("Argument eventType must not be null");
    }
    if (eventHandler == null) {
      throw new NullPointerException("Argument eventHandler must not be null");
    }

    List<EventHandler<? super PreferencesFxEvent>> list = this.eventHandlers.get(eventType);
    if (list != null) {
      list.remove(eventHandler);
    }
  }

  private void fireEvent(PreferencesFxEvent event) {
    List<EventHandler<? super PreferencesFxEvent>> list = this.eventHandlers.get(event.getEventType());
    if (list == null) {
      return;
    }
    for (EventHandler<? super PreferencesFxEvent> eventHandler : list) {
      if (!event.isConsumed()) {
        eventHandler.handle(event);
      }
    }
  }

  /**
   * Saves the settings, when {@link #isSaveSettings()} returns {@code true}.
   */
  public void saveSettings() {
    if (isSaveSettings()) {
      saveSettingValues();
      fireEvent(PreferencesFxEvent.preferencesSavedEvent());
    }
    history.clear(false);
  }

  public void discardChanges() {
    history.clear(true);
    // save settings after undoing them
    if (saveSettings) {
      saveSettingValues();
    }
    fireEvent(PreferencesFxEvent.preferencesNotSavedEvent());
  }

}
