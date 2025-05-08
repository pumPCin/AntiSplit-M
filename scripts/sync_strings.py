import os
import xml.etree.ElementTree as ET
from pathlib import Path


def sync_strings():
    # Path to the project's res directory
    res_dir = Path("app/src/main/res")

    # Path to default strings.xml
    default_strings = res_dir / "values/strings.xml"

    if not default_strings.exists():
        print("Error: Default strings.xml not found")
        return

    # Parse default strings
    default_tree = ET.parse(default_strings)
    default_root = default_tree.getroot()
    default_strings_set = {elem.attrib["name"] for elem in default_root}

    # Find all language directories (values-xx or values-xx-rYY)
    for lang_dir in res_dir.glob("values-*"):
        parts = lang_dir.name.split("-")

        # Skip non-language folders (like values-land, values-v21, etc.)
        if len(parts) == 1:  # Shouldn't happen but just in case
            continue
        if len(parts) == 2:
            if not (len(parts[1]) == 2 and parts[1].isalpha()):  # Not a language code
                continue
        elif len(parts) == 3:
            if not (len(parts[1]) == 2 and parts[1].isalpha() and parts[2].startswith('r') and len(parts[2]) > 1):
                continue
        else:  # More than 3 parts - not a language folder
            continue

        lang_strings = lang_dir / "strings.xml"

        if not lang_strings.exists():
            print(f"Creating new strings.xml for {lang_dir.name}")
            # Create new file with all default strings in original order
            new_root = ET.Element("resources")
            for elem in default_root:
                new_elem = ET.Element(elem.tag, elem.attrib)
                new_elem.text = elem.text
                new_elem.tail = elem.tail
                new_root.append(new_elem)
            ET.ElementTree(new_root).write(
                lang_strings, encoding="utf-8", xml_declaration=True
            )
            continue

        # Parse existing language strings
        lang_tree = ET.parse(lang_strings)
        lang_root = lang_tree.getroot()
        lang_strings_set = {elem.attrib["name"] for elem in lang_root}

        # Find missing strings
        missing_strings = default_strings_set - lang_strings_set
        if not missing_strings:
            print(f"No missing strings in {lang_dir.name}")
            continue

        print(f"Adding {len(missing_strings)} missing strings to {lang_dir.name}")
        # Create a mapping of default string positions
        default_positions = {elem.attrib["name"]: i for i, elem in enumerate(default_root)}

        # Insert missing strings in correct positions
        for elem in default_root:
            if elem.attrib["name"] in missing_strings:
                new_elem = ET.Element(elem.tag, elem.attrib)
                new_elem.text = elem.text
                new_elem.tail = elem.tail
                # Find insertion position based on default order
                insert_pos = 0
                for i, existing_elem in enumerate(lang_root):
                    if existing_elem.attrib["name"] in default_positions:
                        if default_positions[elem.attrib["name"]] < default_positions[existing_elem.attrib["name"]]:
                            insert_pos = i
                            break
                lang_root.insert(insert_pos, new_elem)

        # Write updated file
        ET.ElementTree(lang_root).write(
            lang_strings, encoding="utf-8", xml_declaration=True
        )


if __name__ == "__main__":
    sync_strings()
