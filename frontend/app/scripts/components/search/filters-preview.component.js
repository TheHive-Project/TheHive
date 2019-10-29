(function() {
    'use strict';
    angular.module('theHiveComponents')
        .component('filtersPreview', {
            controller: function() {
                this.clearItem = function(field) {
                    this.onClearItem({
                        field: field
                    });
                };

                this.clearAll = function() {
                    this.onClearAll();
                };
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/search/filters-preview.component.html',
            bindings: {
                filters: '<',
                onClearItem: '&',
                onClearAll: '&'
            }
        });
})();
