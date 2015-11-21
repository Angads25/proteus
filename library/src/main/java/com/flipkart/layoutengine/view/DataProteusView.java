package com.flipkart.layoutengine.view;

import android.support.annotation.Nullable;
import android.view.View;

import com.flipkart.layoutengine.DataContext;
import com.flipkart.layoutengine.ParserContext;
import com.flipkart.layoutengine.binding.Binding;
import com.flipkart.layoutengine.exceptions.InvalidDataPathException;
import com.flipkart.layoutengine.exceptions.JsonNullException;
import com.flipkart.layoutengine.exceptions.NoSuchDataPathException;
import com.flipkart.layoutengine.parser.Attributes;
import com.flipkart.layoutengine.provider.ProteusConstants;
import com.flipkart.layoutengine.toolbox.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * A {@link ProteusView} implementation to update the data associated with a {@link android.view.View}
 * built using a {@link com.flipkart.layoutengine.builder.LayoutBuilder}.
 *
 * @author Aditya Sharat {@literal <aditya.sharat@flipkart.com>}
 */
public class DataProteusView extends SimpleProteusView {

    private boolean isViewUpdating = false;
    private String dataPathForChildren;
    private JsonObject childLayout;
    private OnUpdateDataListener onUpdateDataListeners;

    /**
     * This Array holds a to the {@link Binding}s of this {@link DataProteusView}.
     */
    private ArrayList<Binding> bindings;
    private ParserContext parserContext;
    private Logger logger = LoggerFactory.getLogger(DataProteusView.class);

    public DataProteusView(ProteusView proteusView) {
        super(proteusView.getView(), proteusView.getLayout(), proteusView.getIndex(),
                proteusView.getChildren(), proteusView.getParent());

        if (proteusView instanceof DataProteusView) {
            DataProteusView dataProteusView = (DataProteusView) proteusView;
            parserContext = dataProteusView.getParserContext();
            bindings = dataProteusView.getBindings();
            dataPathForChildren = dataProteusView.getDataPathForChildren();
            childLayout = dataProteusView.getChildLayout();
            onUpdateDataListeners = dataProteusView.getOnUpdateDataListeners();
        }
    }

    @Override
    public void replaceView(ProteusView view) {
        super.replaceView(view);
        if (view instanceof DataProteusView) {
            DataProteusView dataProteusView = (DataProteusView) view;
            this.bindings = dataProteusView.getBindings();
            this.parserContext = dataProteusView.getParserContext();
            this.childLayout = dataProteusView.getChildLayout();
            this.dataPathForChildren = dataProteusView.getDataPathForChildren();
        }
    }

    public void addBinding(Binding binding) {
        if (this.bindings == null) {
            this.bindings = new ArrayList<>();
        }
        this.bindings.add(binding);
    }

    @Override
    protected View updateDataImpl(JsonObject data) {
        if (logger.isDebugEnabled()) {
            logger.debug("START: update data " + (data != null ? "(top-level)" : "")
                    + "for view with " + Utils.getLayoutIdentifier(layout));
        }
        this.isViewUpdating = true;

        data = onBeforeUpdateData(data);

        // update the data context so all child views can refer to new data
        if (data != null) {
            updateDataContext(data);
        }

        if (parserContext != null) {
            data = onAfterDataContext(parserContext.getDataContext().getDataProvider().getData().getAsJsonObject());
        } else {
            data = onAfterDataContext(null);
        }

        if (data == null) {
            return this.getView();
        }

        // update the bindings of this view
        if (this.bindings != null) {
            for (Binding binding : this.bindings) {
                this.handleBinding(binding);
            }
        }
        // update the child views
        if (dataPathForChildren != null) {
            if (children == null) {
                children = new ArrayList<>();
            }
            updateChildrenFromData();
        } else if (children != null) {
            for (ProteusView proteusView : children) {
                proteusView.updateData(null);
            }
        }

        this.isViewUpdating = false;
        if (logger.isDebugEnabled()) {
            logger.debug("END: update data " + (data != null ? "(top-level)" : "")
                    + "for view with " + Utils.getLayoutIdentifier(layout));
        }

        onUpdateDataComplete();
        return this.getView();
    }

    @Nullable
    private JsonObject onBeforeUpdateData(JsonObject data) {
        if (onUpdateDataListeners != null) {
            JsonObject override = onUpdateDataListeners.onBeforeUpdateData(data);
            if (override != null) {
                return override;
            }
        }
        return data;
    }

    @Nullable
    private JsonObject onAfterDataContext(JsonObject data) {
        if (onUpdateDataListeners != null) {
            JsonObject override = onUpdateDataListeners.onAfterDataContext(data);
            if (override != null) {
                return override;
            }
        }
        return data;
    }

    private void onUpdateDataComplete() {
        if (onUpdateDataListeners != null) {
            onUpdateDataListeners.onUpdateDataComplete();
        }
    }

    private void updateDataContext(JsonObject data) {
        parserContext.getDataContext().updateDataContext(data);
    }

    private void updateChildrenFromData() {
        JsonArray childrenDataArray = new JsonArray();
        try {
            childrenDataArray = Utils.getElementFromData(dataPathForChildren,
                    parserContext.getDataContext().getDataProvider(), index).getAsJsonArray();
        } catch (JsonNullException | NoSuchDataPathException | InvalidDataPathException | IllegalStateException e) {
            if (logger.isErrorEnabled()) {
                logger.error("#updateChildrenFromData " + e.getMessage());
            }
        }

        if (children.size() > childrenDataArray.size()) {
            while (children.size() > childrenDataArray.size()) {
                ProteusView proteusView = children.remove(children.size() - 1);
                unsetParent(proteusView.getView());
                proteusView.destroy();
            }
        }

        JsonObject data = parserContext.getDataContext().getDataProvider().getData().getAsJsonObject();
        for (int index = 0; index < childrenDataArray.size(); index++) {
            if (index < children.size()) {
                children.get(index).updateData(data);
            } else {
                if (childLayout != null) {
                    DataProteusView child = (DataProteusView) parserContext.getLayoutBuilder().build(view,
                            childLayout, data, index, styles);
                    addView(child);
                }
            }
        }
    }

    /**
     * Updates the Binding with new data. It uses a {@link com.flipkart.layoutengine.binding.Binding}
     * to get the associated {@link android.view.View}, {@link com.flipkart.layoutengine.builder.LayoutBuilder},
     * and {@link com.flipkart.layoutengine.parser.LayoutHandler} to update the value of the bound
     * attribute with the new value fetched from the new data object passed.
     *
     * @param binding The property name to update mapped to its
     *                {@link com.flipkart.layoutengine.binding.Binding}
     */
    private void handleBinding(Binding binding) {
        if (binding.hasRegEx()) {
            parserContext.getLayoutBuilder().handleAttribute(
                    binding.getLayoutHandler(),
                    parserContext,
                    binding.getAttributeKey(),
                    new JsonPrimitive(binding.getAttributeValue()),
                    layout,
                    this,
                    parent,
                    index);
        } else {
            JsonElement dataValue;
            try {
                dataValue = Utils.getElementFromData(binding.getBindingName(),
                        parserContext.getDataContext().getDataProvider(), index);
                if (shouldSetVisibility(binding.getAttributeKey(), view)) {
                    this.getView().setVisibility(View.VISIBLE);
                }
            } catch (JsonNullException | NoSuchDataPathException | InvalidDataPathException e) {
                if (logger.isErrorEnabled()) {
                    logger.error("#handleBinding() " + e.getMessage());
                }
                if (shouldSetVisibility(binding.getAttributeKey(), view)) {
                    getView().setVisibility(View.GONE);
                }
                dataValue = new JsonPrimitive(ProteusConstants.DATA_NULL);
            }
            parserContext.getLayoutBuilder().handleAttribute(
                    binding.getLayoutHandler(),
                    parserContext,
                    binding.getAttributeKey(),
                    dataValue,
                    layout,
                    this,
                    parent,
                    index);

        }
    }

    public boolean isViewUpdating() {
        return isViewUpdating;
    }

    public ArrayList<Binding> getBindings() {
        return bindings;
    }

    public void setParserContext(ParserContext parserContext) {
        this.parserContext = parserContext;
    }

    public ParserContext getParserContext() {
        return parserContext;
    }

    public JsonElement get(String dataPath, int childIndex) {
        return parserContext.getDataContext().get(dataPath, childIndex);
    }

    public void set(String dataPath, JsonElement newValue, int childIndex) {
        if (dataPath == null) {
            return;
        }

        String aliasedDataPath = DataContext.getAliasedDataPath(dataPath,
                parserContext.getDataContext().getReverseScopeMap(), true);

        JsonElement parent = null;
        try {
            parent = Utils.getElementFromData(aliasedDataPath.substring(0, aliasedDataPath.lastIndexOf(".")),
                    parserContext.getDataContext().getDataProvider(), childIndex);
        } catch (JsonNullException | NoSuchDataPathException | InvalidDataPathException e) {
            if (logger.isErrorEnabled()) {
                logger.error("#set() " + e.getMessage());
            }
        }
        if (parent == null || !parent.isJsonObject()) {
            return;
        }

        String propertyName = aliasedDataPath.substring(aliasedDataPath.lastIndexOf(".") + 1, aliasedDataPath.length());
        parent.getAsJsonObject().add(propertyName, newValue);

        updateView(aliasedDataPath);
    }

    public void set(String dataPath, String newValue, int childIndex) {
        set(dataPath, new JsonPrimitive(newValue), childIndex);
    }

    public void set(String dataPath, Number newValue, int childIndex) {
        set(dataPath, new JsonPrimitive(newValue), childIndex);
    }

    public void set(String dataPath, boolean newValue, int childIndex) {
        set(dataPath, new JsonPrimitive(newValue), childIndex);
    }

    private void updateView(String dataPath) {
        this.isViewUpdating = true;
        if (this.bindings != null) {
            for (Binding binding : this.bindings) {
                if (binding.getBindingName().equals(dataPath)) {
                    this.handleBinding(binding);
                }
            }
        }

        if (getChildren() != null) {
            for (ProteusView proteusView : getChildren()) {
                DataProteusView dataProteusView = (DataProteusView) proteusView;
                String aliasedDataPath = DataContext.getAliasedDataPath(dataPath,
                        dataProteusView.getParserContext().getDataContext().getReverseScopeMap(), false);
                dataProteusView.updateView(aliasedDataPath);
            }
        }

        this.isViewUpdating = false;
    }

    public String getDataPathForChildren() {
        return dataPathForChildren;
    }

    public void setDataPathForChildren(String dataPathForChildren) {
        this.dataPathForChildren = dataPathForChildren;
    }

    public void setChildLayout(JsonObject childLayout) {
        this.childLayout = childLayout;
    }

    public JsonObject getChildLayout() {
        return childLayout;
    }

    @Override
    public void destroy() {
        super.destroy();
        childLayout = null;
        parserContext = null;
        dataPathForChildren = null;
        onUpdateDataListeners = null;
        bindings = null;
    }

    public void addOnUpdateDataListener(OnUpdateDataListener listener) {
        onUpdateDataListeners = listener;
    }

    public void removeOnUpdateDataListener() {
        onUpdateDataListeners = null;

    }

    public OnUpdateDataListener getOnUpdateDataListeners() {
        return onUpdateDataListeners;
    }

    public interface OnUpdateDataListener {

        JsonObject onBeforeUpdateData(JsonObject data);

        JsonObject onAfterDataContext(JsonObject data);

        void onUpdateDataComplete();
    }

    public static boolean shouldSetVisibility(String attribute, View view) {
        return !Attributes.View.Visibility.getName().equals(attribute) &&
                !Attributes.View.Invisibility.getName().equals(attribute) &&
                view != null;
    }
}
