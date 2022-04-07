
var countryPickerInputElements = document.getElementsByClassName('autocomplete__input');
var form = document.getElementsByTagName('form')[0];

if(form) {
    form.addEventListener('submit', function () {
        for (var i = 0; i < countryPickerInputElements.length; i++) {
            var input = countryPickerInputElements[i];
            var select = countryPickerInputElements[i].parentNode.parentNode.parentNode.querySelector('select');
            if (input.value.trim() == "") select.selectedIndex = 0
        }
    });
}
