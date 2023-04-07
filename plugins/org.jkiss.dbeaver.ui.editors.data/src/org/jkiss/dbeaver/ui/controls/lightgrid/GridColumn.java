/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.controls.lightgrid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetPreferences;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid column info.
 * Holds information about column width and other UI properties
 *
 * @author serge@dbeaver.com
 */
public class GridColumn implements IGridColumn {

    /**
     * Default width of the column.
     */
    private static final int DEFAULT_WIDTH = 10;

    static final int topMargin = 6;
    static final int bottomMargin = 6;
    private static final int leftMargin = 6;
    private static final int rightMargin = 6;
    private static final int imageSpacing = 3;
    private static final int insideMargin = 3;

    private final LightGrid grid;
    private final Object element;
    private final GridColumn parent;
    private List<GridColumn> children;

    private int level;
    private int width = DEFAULT_WIDTH;
    private int height = -1;
    private int pinIndex = -1;

    public GridColumn(LightGrid grid, Object element) {
        this.grid = grid;
        this.element = element;
        this.parent = null;
        this.level = 0;
        grid.newColumn(this, -1);
    }

    public GridColumn(GridColumn parent, Object element) {
        this.grid = parent.grid;
        this.element = element;
        this.parent = parent;
        this.level = parent.level + 1;
        parent.addChild(this);
        grid.newColumn(this, -1);
    }

    @Override
    public Object getElement() {
        return element;
    }

    @Override
    public int getIndex() {
        return grid.indexOf(this);
    }

    /**
     * Returns the width of the column.
     *
     * @return width of column
     */
    public int getWidth() {
        return width;
    }

    /**
     * Sets the width of the column.
     *
     * @param width new width
     */
    public void setWidth(int width) {
        setWidth(width, true);
    }

    void setWidth(int width, boolean redraw) {
        int delta = width - this.width;
        this.width = width;
        for (GridColumn pc = parent; pc != null; pc = pc.parent) {
            pc.width += delta;
        }
        if (redraw) {
            grid.setScrollValuesObsolete();
            grid.redraw();
        }
    }

    public boolean isPinned() {
        return pinIndex >= 0 || parent != null && parent.isPinned();
    }

    public int getPinIndex() {
        return parent == null ? pinIndex : parent.getPinIndex();
    }

    public void setPinIndex(int pinIndex) {
        this.pinIndex = pinIndex;
    }

    public boolean isOverFilterButton(int x, int y) {
        if (!isFilterable()) {
            return false;
        }
        Rectangle bounds = getBounds();
        if (y < bounds.y || y > bounds.y + bounds.height) {
            return false;
        }
        Rectangle filterBounds = GridColumnRenderer.getFilterControlBounds();

        int filterEnd = bounds.width - GridColumnRenderer.IMAGE_SPACING;
        int filterBegin = filterEnd - filterBounds.width;

        boolean isOverIcon = x >= filterBegin && x <= filterEnd &&
            y < bounds.y + filterBounds.height + GridColumnRenderer.TOP_MARGIN;
        return isOverIcon;
    }

    public boolean isOverSortArrow(int x, int y) {
        int sortOrder = grid.getContentProvider().getSortOrder(this);
        if (sortOrder <= 0 && !grid.getContentProvider().isElementSupportsSort(this)) {
            return false;
        }
        Rectangle bounds = getBounds();
        if (y < bounds.y || y > bounds.y + bounds.height) {
            return false;
        }
        int arrowEnd = bounds.width - GridColumnRenderer.IMAGE_SPACING - GridColumnRenderer.getFilterControlBounds().width;
        Rectangle sortBounds = GridColumnRenderer.getSortControlBounds();
        int arrowBegin = arrowEnd - sortBounds.width;
        return
            x >= arrowBegin && x <= arrowEnd &&
                y <= bounds.y + sortBounds.height + GridColumnRenderer.TOP_MARGIN;
    }

    public boolean isOverIcon(int x, int y) {
        Rectangle bounds = getBounds();
        if (y < bounds.y || y > bounds.y + bounds.height) {
            return false;
        }
        Image image = grid.getLabelProvider().getImage(this);
        if (image == null) {
            return false;
        }
        Rectangle imgBounds = image.getBounds();
        if (x >= bounds.x + GridColumnRenderer.LEFT_MARGIN &&
            x <= bounds.x + GridColumnRenderer.LEFT_MARGIN + imgBounds.width + GridColumnRenderer.IMAGE_SPACING &&
            y > bounds.y + GridColumnRenderer.TOP_MARGIN &&
            y <= bounds.y + GridColumnRenderer.TOP_MARGIN + imgBounds.height) {
            return true;
        }
        return false;
    }

    int getHeaderHeight(boolean includeChildren, boolean forceRefresh) {
        if (forceRefresh) {
            height = -1;
        }
        if (height < 0) {
            height = topMargin + grid.fontMetrics.getHeight() + bottomMargin;
            Image image = grid.getLabelProvider().getImage(this);
            if (image != null) {
                height = Math.max(height, topMargin + image.getBounds().height + bottomMargin);
            }
            final String description = grid.getLabelProvider().getDescription(this);
            if (!CommonUtils.isEmpty(description)) {
                height += topMargin + grid.fontMetrics.getHeight();
            }
        }
        int childHeight = 0;
        if (includeChildren && !CommonUtils.isEmpty(children)) {
            for (GridColumn child : children) {
                childHeight = Math.max(childHeight, child.getHeaderHeight(true, false));
            }
        }
        return height + childHeight;
    }

    int computeHeaderWidth() {
        int x = leftMargin;
        final IGridLabelProvider labelProvider = grid.getLabelProvider();
        Image image = labelProvider.getImage(this);
        if (image != null) {
            x += image.getBounds().width + imageSpacing;
        }
        {
            int textWidth;
            Object calcWidthMethod = labelProvider.getGridOption(IGridLabelProvider.OPTION_CALC_COLUMN_WIDTH_METHOD);
            String text = "X";
            switch ((ResultSetPreferences.GridColumnCalcWidthMethod) calcWidthMethod) {
                case TITLE_AND_VALUES:
                    text = labelProvider.getText(this);
                    textWidth = grid.sizingGC.stringExtent(text).x;
                    break;
                case VALUES:
                    textWidth = grid.sizingGC.stringExtent(text).x;
                    break;
                case TITLE_DESCRIPTION_VALUES:
                    text = labelProvider.getText(this);
                    textWidth = grid.sizingGC.stringExtent(text).x;
                    if (Boolean.TRUE.equals(labelProvider.getGridOption(IGridLabelProvider.OPTION_SHOW_DESCRIPTION))) {
                        String description = labelProvider.getDescription(this);
                        if (!CommonUtils.isEmpty(description)) {
                            int descWidth = grid.sizingGC.stringExtent(description).x;
                            if (descWidth > textWidth) {
                                textWidth = descWidth;
                            }
                        }
                    }
                    break;
                default:
                    textWidth = grid.sizingGC.stringExtent("X").x;
                    break;
            }
            x += textWidth + rightMargin;
        }
        if (isSortable()) {
            x += rightMargin + GridColumnRenderer.getSortControlBounds().width + GridColumnRenderer.IMAGE_SPACING;
        }

        x += GridColumnRenderer.getFilterControlBounds().width;

        if (!CommonUtils.isEmpty(children)) {
            int childWidth = 0;
            for (GridColumn child : children) {
                childWidth += child.computeHeaderWidth();
            }
            return Math.max(x, childWidth);
        }

        return x;
    }

    public boolean isSortable() {
        return grid.getContentProvider().getSortOrder(this) != SWT.NONE;
    }

    public boolean isFilterable() {
        return grid.getContentProvider().isElementSupportsFilter(this);
    }

    /**
     * Causes the receiver to be resized to its preferred size.
     */
    void pack(boolean reflect) {
        int newWidth = computeHeaderWidth();
        if (CommonUtils.isEmpty(children)) {
            // Calculate width of visible cells
            int topIndex = grid.getTopIndex();
            int bottomIndex = grid.getBottomIndex();
            if (topIndex >= 0 && bottomIndex >= topIndex) {
                int itemCount = grid.getItemCount();
                for (int i = topIndex; i <= bottomIndex && i < itemCount; i++) {
                    newWidth = Math.max(newWidth, computeCellWidth(grid.getRow(i)));
                }
            }
        } else {
            int childrenWidth = 0;
            for (GridColumn child : children) {
                child.pack(reflect);
                childrenWidth += child.getWidth();
            }
            if (newWidth > childrenWidth) {
                // Header width bigger than children width
                GridColumn lastChild = children.get(children.size() - 1);
                lastChild.setWidth(lastChild.getWidth() + newWidth - childrenWidth);
            } else {
                newWidth = childrenWidth;
            }
        }
        if (reflect) {
            setWidth(newWidth, false);
        } else {
            this.width = newWidth;
        }
    }

    private int computeCellWidth(IGridRow row) {
        int x = 0;

        x += leftMargin;

        IGridContentProvider.CellInformation cellInfo = grid.getContentProvider().getCellInfo(
            this, row, false);

        String cellText = grid.getCellText(cellInfo.text);
        int state = cellInfo.state;
        Rectangle imageBounds;
        if (GridCellRenderer.isLinkState(state)) {
            imageBounds = GridCellRenderer.LINK_IMAGE_BOUNDS;
        } else {
            DBPImage image = cellInfo.image;
            imageBounds = image == null ? null : DBeaverIcons.getImage(image).getBounds();
        }
        if (imageBounds != null) {
            x += imageBounds.width + insideMargin;
        }

        x += grid.sizingGC.textExtent(cellText).x + rightMargin;
        return x;
    }

    /**
     * Returns the bounds of this column's header.
     *
     * @return bounds of the column header
     */
    Rectangle getBounds() {
        Rectangle bounds = new Rectangle(0, 0, 0, 0);

        Point loc = grid.getOrigin(this, -1);
        bounds.x = loc.x;
        bounds.y = loc.y;
        bounds.width = getWidth();
        bounds.height = grid.getHeaderHeight();

        return bounds;
    }

    /**
     * Returns the parent grid.
     *
     * @return the parent grid.
     */
    public LightGrid getGrid() {
        return grid;
    }

    /**
     * Returns the tooltip of the column header.
     *
     * @return the tooltip text
     */
    @Nullable
    public String getHeaderTooltip() {
        String tip = grid.getLabelProvider().getToolTipText(this);
        if (tip == null) {
            tip = grid.getLabelProvider().getText(this);
        }
        return tip;
    }

    @Override
    public GridColumn getParent() {
        return parent;
    }

    @Override
    public List<GridColumn> getChildren() {
        return children;
    }

    private void addChild(GridColumn gridColumn) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(gridColumn);
    }

    private void removeChild(GridColumn column) {
        children.remove(column);
    }

    public int getLevel() {
        return level;
    }

    public boolean isParent(GridColumn col) {
        for (GridColumn p = parent; p != null; p = p.parent) {
            if (p == col) {
                return true;
            }
        }
        return false;
    }

    public GridColumn getFirstLeaf() {
        if (children == null) {
            return this;
        } else {
            return children.get(0).getFirstLeaf();
        }
    }

    @Override
    public String toString() {
        return CommonUtils.toString(element);
    }

}
