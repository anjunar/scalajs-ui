import { button, classes, div, listProperty, onClick, property } from "@anjunar/scalajs-ui-core";
import { arrayForm, form, input, inputContainer, Size, subForm } from "@anjunar/scalajs-ui-forms";
import { translated } from "../../app/i18n.js";

class AddressModel {
  @Size(1, 60)
  readonly city = property("");
}

class CompositionModel {
  readonly tags = listProperty<string>(["typescript"]);
  readonly address = property(new AddressModel());
}

export function formsCompositionPage(): void {
  const model = new CompositionModel();

  form(model, () => {
    div(() => {
      classes("flex", "flex-col", "gap-4");

      // arrayForm binds to the enclosing form's ListProperty by name. A
      // fieldSet around it would only group it visually -- it adds no level
      // to the model -- so a plain div does the same job here.
      div(() => {
        classes("flex", "flex-col", "gap-2");
        arrayForm("tags", (index) => {
          input(`tags-${index}`);
        });
        button(translated("Add tag"), {}, () => {
          classes("px-3", "py-1.5");
          onClick(() => model.tags.add(""));
        });
      });

      subForm("address", model.address.get, () => {
        inputContainer({ label: translated("City").get }, () => {
          input("city");
        });
      });
    });
  });
}
