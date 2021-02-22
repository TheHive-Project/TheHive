(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .controller('TaxonomySelectionModalCtrl', function($uibModalInstance, taxonomies) {
            var self = this;

            this.taxonomies = angular.copy(taxonomies);

            this.formData = {
                selectedTaxonomy: null,
                selectedTags: null
            };

            this.addSelectedTags = function() {
                if (!self.formData.selectedTaxonomy) {
                    return;
                }

                var selection = _.filter(self.formData.selectedTaxonomy.tags, function(tag) {
                    return tag.selected;
                });

                if (selection.length === 0) {
                    return;
                }

                $uibModalInstance.close(selection);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        });
})();
