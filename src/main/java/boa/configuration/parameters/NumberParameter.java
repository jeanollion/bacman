/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;

import boa.configuration.parameters.ui.NumberParameterUI;
import boa.configuration.parameters.ui.ParameterUI;
import boa.plugins.ops.OpParameter;
import boa.plugins.ops.ParameterWithValue;
import java.util.function.Consumer;

/**
 *
 * @author Jean Ollion
 */
public class NumberParameter<P extends NumberParameter<P>> extends ParameterImpl<P> implements Listenable<P>, OpParameter<P> {
    Number value;
    int decimalPlaces;
    
    public NumberParameter(String name, int decimalPlaces) {
        super(name);
        this.decimalPlaces=decimalPlaces;
    }
    
    public NumberParameter(String name, int decimalPlaces, Number defaultValue) {
        this(name, decimalPlaces);
        this.value=defaultValue;
    }
    
    public ParameterUI getUI() {
        return new NumberParameterUI(this);
    }
    
    public int getDecimalPlaceNumber() {
        return decimalPlaces;
    }
    
    public Number getValue() {
        return value;
    }
    
    public void setValue(Number value) {
        this.value=value;
        this.fireListeners();
    }
    @Override 
    public boolean isValid() {
        if (!super.isValid()) return false;
        return value!=null;
    }
    @Override
    public String toString() {
        return name+": "+value;
    }
    
    public boolean hasIntegerValue() {return (getValue().doubleValue()-getValue().intValue())!=0;}
    
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof NumberParameter) {
            if (((NumberParameter)other).getValue().doubleValue()!=getValue().doubleValue()) {
                logger.debug("Number: {}!={} value: {} vs {}", this, other, getValue(), ((NumberParameter)other).getValue() );
                return false;
            } else return true;
        }
        else return false;
    }
    @Override 
    public void setContentFrom(Parameter other) {
        if (other instanceof NumberParameter) {
            this.value=((NumberParameter)other).getValue();
        }
    }
    
    @Override public P duplicate() {
        NumberParameter res =  new NumberParameter(name, decimalPlaces, value);
        res.setListeners(listeners);
        res.addValidationFunction(additionalValidation);
        return (P)res;
    }

    @Override
    public Object toJSONEntry() {
        return value;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        this.value=(Number)jsonEntry;
    }
    
}
