#!/usr/bin/env python3
"""
Convert JavaFX FXML files to libGDX Scene2D Java code.

Generates a Java class per FXML file that builds the equivalent UI layout
using Scene2D Table, ScrollPane, and standard widgets.

Widget mapping:
  GridPane        -> Table (with row()/columnDefaults())
  VBox            -> Table (vertical, one widget per row)
  HBox            -> Table (horizontal, one widget per cell in same row)
  Label           -> Label
  ComboBox        -> SelectBox<String>
  CheckBox        -> CheckBox
  Spinner         -> SpinnerWidget (custom composite: TextField + buttons)
  NumericSpinner  -> SpinnerWidget
  Slider          -> Slider
  TextField       -> TextField
  PasswordField   -> TextField (passwordMode=true)
  Button          -> TextButton
  Hyperlink       -> TextButton (styled as link)
  ListView        -> List<String>
  TableView       -> (stub comment, needs manual DataTableWidget)
  TabPane + Tab   -> ConfigTabPane (custom)
  ProgressBar     -> ProgressBar
  ScrollPane      -> ScrollPane
  fx:include      -> method call to sub-tab builder

Usage:
  python3 tools/fxml2scene2d.py src/bms/player/beatoraja/launcher/VideoConfigurationView.fxml
  python3 tools/fxml2scene2d.py src/bms/player/beatoraja/launcher/*.fxml
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from textwrap import indent

NS = {
    '': 'http://javafx.com/javafx/8.0.65',
    'fx': 'http://javafx.com/fxml/1',
}

# Strip namespace from tag
def tag(elem):
    t = elem.tag
    if '}' in t:
        t = t.split('}', 1)[1]
    return t


def fxid(elem):
    return elem.get('{http://javafx.com/fxml/1}id') or elem.get('fx:id')


def i18n(text):
    """Convert %KEY to bundle.get("KEY"), or quoted literal."""
    if text and text.startswith('%'):
        return f'bundle.get("{text[1:]}")'
    return f'"{text}"' if text else '""'


def grid_row(elem):
    return int(elem.get('GridPane.rowIndex', '0'))


def grid_col(elem):
    return int(elem.get('GridPane.columnIndex', '0'))


def grid_colspan(elem):
    return int(elem.get('GridPane.columnSpan', '1'))


class Scene2DGenerator:
    def __init__(self):
        self.fields = []       # (type, name) pairs for class fields
        self.lines = []        # lines of build code
        self.indent_level = 2  # base indent (inside method body)
        self.controller = None

    def emit(self, line):
        self.lines.append('    ' * self.indent_level + line)

    def blank(self):
        self.lines.append('')

    def field(self, widget_type, name):
        self.fields.append((widget_type, name))

    def convert_spinner(self, elem):
        name = fxid(elem)
        if not name:
            return
        # Parse valueFactory for min/max/initial/step
        vf = elem.find('.//{http://javafx.com/javafx/8.0.65}IntegerSpinnerValueFactory')
        if vf is None:
            # Try without namespace
            for child in elem.iter():
                t = tag(child)
                if 'IntegerSpinnerValueFactory' in t:
                    vf = child
                    break
        if vf is None:
            for child in elem.iter():
                t = tag(child)
                if 'DoubleSpinnerValueFactory' in t:
                    vf = child
                    break

        min_val = vf.get('min', '0') if vf is not None else '0'
        max_val = vf.get('max', '100') if vf is not None else '100'
        initial = vf.get('initialValue', '0') if vf is not None else '0'
        step = vf.get('amountToStepBy', '1') if vf is not None else '1'

        self.field('SpinnerWidget', name)
        self.emit(f'{name} = new SpinnerWidget(skin, {min_val}, {max_val}, {initial}, {step});')

    def convert_combobox(self, elem):
        name = fxid(elem)
        if not name:
            return
        self.field('SelectBox<String>', name)
        self.emit(f'{name} = new SelectBox<>(skin);')

        # Check for inline items
        items = []
        for child in elem.iter():
            t = tag(child)
            if t == 'String':
                val = child.get('{http://javafx.com/fxml/1}value') or child.get('fx:value', '')
                if val:
                    items.append(val)
        if items:
            arr = ', '.join(f'"{v}"' for v in items)
            self.emit(f'{name}.setItems(new String[]{{{arr}}});')

    def convert_checkbox(self, elem):
        name = fxid(elem)
        if not name:
            return
        text = elem.get('text', '')
        self.field('CheckBox', name)
        self.emit(f'{name} = new CheckBox({i18n(text)}, skin);')

    def convert_slider(self, elem):
        name = fxid(elem)
        if not name:
            return
        min_val = elem.get('min', '0')
        max_val = elem.get('max', '1.0')
        step = elem.get('blockIncrement', '0.1')
        value = elem.get('value', '0')
        self.field('Slider', name)
        self.emit(f'{name} = new Slider({min_val}f, {max_val}f, {step}f, false, skin);')
        self.emit(f'{name}.setValue({value}f);')

    def convert_textfield(self, elem, password=False):
        name = fxid(elem)
        if not name:
            return
        self.field('TextField', name)
        self.emit(f'{name} = new TextField("", skin);')
        if password:
            self.emit(f'{name}.setPasswordCharacter(\'*\');')
            self.emit(f'{name}.setPasswordMode(true);')

    def convert_button(self, elem):
        name = fxid(elem)
        text = elem.get('text', '')
        action = elem.get('onAction', '')
        if not name and not text:
            return
        varname = name or f'btn_{text.replace(" ", "_").replace("+", "Plus").lower()}'
        self.field('TextButton', varname)
        self.emit(f'{varname} = new TextButton({i18n(text)}, skin);')
        if action:
            method = action.lstrip('#')
            self.emit(f'// TODO: {varname}.addListener(new ChangeListener() {{ void changed(...) {{ {method}(); }} }});')

    def convert_hyperlink(self, elem):
        name = fxid(elem)
        if not name:
            return
        text = elem.get('text', '')
        self.field('TextButton', name)
        self.emit(f'{name} = new TextButton({i18n(text)}, skin, "link");')

    def convert_label(self, elem):
        text = elem.get('text', '')
        return f'new Label({i18n(text)}, skin)'

    def convert_listview(self, elem):
        name = fxid(elem)
        if not name:
            return
        self.field('List<String>', name)
        self.emit(f'{name} = new List<>(skin);')

    def convert_progressbar(self, elem):
        name = fxid(elem)
        if not name:
            return
        self.field('ProgressBar', name)
        self.emit(f'{name} = new ProgressBar(0f, 1f, 0.01f, false, skin);')

    def convert_tableview(self, elem):
        name = fxid(elem)
        if not name:
            return
        cols = []
        for col in elem.iter():
            if 'TableColumn' in tag(col):
                col_name = fxid(col)
                col_text = col.get('text', '')
                if col_name:
                    cols.append((col_name, col_text))

        self.emit(f'// TODO: {name} = DataTableWidget with columns:')
        for col_name, col_text in cols:
            self.emit(f'//   {col_name}: {i18n(col_text)}')

    def process_gridpane(self, elem):
        """Convert GridPane to Table with row/col positioning."""
        self.emit('Table grid = new Table(skin);')
        self.emit('grid.defaults().left().pad(4);')
        self.blank()

        # Collect children by row
        rows = {}
        for child in elem:
            t = tag(child)
            if t in ('stylesheets', 'columnConstraints', 'rowConstraints', 'padding'):
                continue
            row = grid_row(child)
            if row not in rows:
                rows[row] = []
            rows[row].append(child)

        for row_idx in sorted(rows.keys()):
            children = sorted(rows[row_idx], key=grid_col)
            for child in children:
                t = tag(child)
                colspan = grid_colspan(child)
                widget_expr = self.convert_widget(child)
                if widget_expr:
                    cs = f'.colspan({colspan})' if colspan > 1 else ''
                    self.emit(f'grid.add({widget_expr}){cs};')
                else:
                    name = fxid(child)
                    if name:
                        cs = f'.colspan({colspan})' if colspan > 1 else ''
                        self.emit(f'grid.add({name}){cs};')
            self.emit('grid.row();')
        self.blank()

    def convert_widget(self, elem):
        """Convert a widget element. Returns an expression string for inline use,
        or None if it emits field-based code (caller should use field name)."""
        t = tag(elem)

        if t == 'Label':
            return self.convert_label(elem)
        elif t == 'ComboBox':
            self.convert_combobox(elem)
            return None
        elif t == 'CheckBox':
            self.convert_checkbox(elem)
            return None
        elif t in ('Spinner', 'NumericSpinner'):
            self.convert_spinner(elem)
            return None
        elif t == 'Slider':
            self.convert_slider(elem)
            return None
        elif t == 'TextField':
            self.convert_textfield(elem)
            return None
        elif t == 'PasswordField':
            self.convert_textfield(elem, password=True)
            return None
        elif t == 'Button':
            self.convert_button(elem)
            return None
        elif t == 'Hyperlink':
            self.convert_hyperlink(elem)
            return None
        elif t == 'ListView':
            self.convert_listview(elem)
            return None
        elif t == 'ProgressBar':
            self.convert_progressbar(elem)
            return None
        elif t == 'TableView':
            self.convert_tableview(elem)
            return None

        return None

    def process_vbox(self, elem, table_var='content'):
        """Convert VBox to a vertical Table."""
        self.emit(f'Table {table_var} = new Table(skin);')
        self.emit(f'{table_var}.top().left().pad(10);')
        self.emit(f'{table_var}.defaults().left().padBottom(4);')
        self.blank()

        for child in elem:
            t = tag(child)
            if t in ('padding', 'margin', 'VBox.margin', 'stylesheets'):
                continue

            if t == 'HBox':
                self.process_hbox_inline(child, table_var)
            elif t == 'GridPane':
                self.process_gridpane(child)
                self.emit(f'{table_var}.add(grid).growX();')
                self.emit(f'{table_var}.row();')
            elif t == 'TabPane':
                self.emit(f'// TODO: TabPane - use ConfigTabPane')
                self.emit(f'{table_var}.row();')
            elif t == 'ScrollPane':
                # Recurse into ScrollPane content
                for sc_child in child:
                    sc_t = tag(sc_child)
                    if sc_t == 'GridPane':
                        self.process_gridpane(sc_child)
                        self.emit(f'ScrollPane scrollPane = new ScrollPane(grid, skin);')
                        self.emit(f'{table_var}.add(scrollPane).grow();')
                    elif sc_t == 'VBox':
                        self.process_vbox(sc_child, 'innerContent')
                        self.emit(f'ScrollPane scrollPane = new ScrollPane(innerContent, skin);')
                        self.emit(f'{table_var}.add(scrollPane).grow();')
                self.emit(f'{table_var}.row();')
            else:
                widget_expr = self.convert_widget(child)
                if widget_expr:
                    self.emit(f'{table_var}.add({widget_expr}).growX();')
                else:
                    name = fxid(child)
                    if name:
                        self.emit(f'{table_var}.add({name}).growX();')
                self.emit(f'{table_var}.row();')
        self.blank()

    def process_hbox_inline(self, elem, parent_var):
        """Convert HBox children as cells in the same row of parent table."""
        widgets_in_row = []
        for child in elem:
            t = tag(child)
            if t in ('HBox.margin', 'Insets', 'padding', 'margin', 'VBox.margin'):
                continue
            widget_expr = self.convert_widget(child)
            if widget_expr:
                widgets_in_row.append(widget_expr)
            else:
                name = fxid(child)
                if name:
                    widgets_in_row.append(name)
        for w in widgets_in_row:
            self.emit(f'{parent_var}.add({w}).padRight(8);')
        self.emit(f'{parent_var}.row();')

    def process_root(self, root):
        t = tag(root)
        ctrl = root.get('{http://javafx.com/fxml/1}controller') or root.get('fx:controller', '')
        if ctrl:
            self.controller = ctrl.split('.')[-1]

        if t == 'ScrollPane':
            # Root is a ScrollPane wrapping content
            for child in root:
                ct = tag(child)
                if ct == 'GridPane':
                    self.process_gridpane(child)
                    self.emit('ScrollPane scrollPane = new ScrollPane(grid, skin);')
                    self.emit('return scrollPane;')
                elif ct == 'VBox':
                    self.process_vbox(child)
                    self.emit('ScrollPane scrollPane = new ScrollPane(content, skin);')
                    self.emit('return scrollPane;')
        elif t == 'VBox':
            self.process_vbox(root)
            self.emit('return content;')
        elif t == 'GridPane':
            self.process_gridpane(root)
            self.emit('return grid;')
        else:
            self.emit(f'// Unsupported root element: {t}')
            self.emit('return new Table(skin);')


def convert_fxml(path):
    tree = ET.parse(path)
    root = tree.getroot()

    gen = Scene2DGenerator()
    gen.process_root(root)

    stem = Path(path).stem
    class_name = stem.replace('View', 'Tab').replace('Configuration', '')
    if class_name == 'PlayTab':
        class_name = 'LauncherApp'

    print(f'// Auto-generated from {Path(path).name} by fxml2scene2d.py')
    print(f'// Review and adjust — this is a starting point, not final code.')
    print(f'//')
    print(f'// Original controller: {gen.controller or "unknown"}')
    print()
    print(f'/* === Fields (add to class) === */')
    for ftype, fname in gen.fields:
        print(f'    private {ftype} {fname};')
    print()
    print(f'/* === Build method === */')
    print(f'    private Actor build{class_name}(Skin skin, ResourceBundle bundle) {{')
    for line in gen.lines:
        print(f'    {line}')
    print(f'    }}')
    print()


def main():
    if len(sys.argv) < 2:
        print(f'Usage: {sys.argv[0]} <fxml-file> [<fxml-file> ...]', file=sys.stderr)
        sys.exit(1)

    for path in sys.argv[1:]:
        print(f'\n{"=" * 70}')
        print(f'// File: {path}')
        print(f'{"=" * 70}')
        convert_fxml(path)


if __name__ == '__main__':
    main()
